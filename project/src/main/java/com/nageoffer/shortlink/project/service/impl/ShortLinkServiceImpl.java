/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.shortlink.project.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nageoffer.shortlink.project.dao.entity.ShortLinkDO;
import com.nageoffer.shortlink.project.dao.mapper.ShortLinkMapper;
import com.nageoffer.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.nageoffer.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.nageoffer.shortlink.project.service.ShortLinkService;
import com.nageoffer.shortlink.project.toolkit.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 短链接接口实现层
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：link）获取项目资料
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements ShortLinkService {
    private final RBloomFilter<String> bloomFilter;
    @Override
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam) {
        String suffix = generateSuffix(requestParam);
        ShortLinkDO shortLinkDO = BeanUtil.copyProperties(requestParam, ShortLinkDO.class);//转换为数据对象
        shortLinkDO.setShortUri(suffix);
        shortLinkDO.setEnableStatus(0);//设置为启用状态
        shortLinkDO.setFullShortUrl(requestParam.getDomain()+"/"+suffix);//设置短链接后缀 拼接成完整的短链接
        try {
            baseMapper.insert(shortLinkDO);
        } catch (DuplicateKeyException ex) {
            LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, requestParam.getDomain() + "/" + suffix);
           ShortLinkDO hasShoutLinkDO = baseMapper.selectOne(queryWrapper);
           if(hasShoutLinkDO != null){
               log.warn("短链接：{}重复入库",requestParam.getDomain()+"/"+suffix);
               throw new SecurityException("短链接重复，请稍后重试");//说明布隆过滤器误判了认为没有 实际上数据库里面有
           }

        }
        bloomFilter.add(requestParam.getDomain()+"/"+suffix);
        return ShortLinkCreateRespDTO.builder()
                .fullShortUrl(shortLinkDO.getFullShortUrl())
                .originUrl(requestParam.getDescribe())
                .gid(requestParam.getGid())
                .build();
    }
    private  String generateSuffix(ShortLinkCreateReqDTO requestParam) {
        int count=0;//重试次数
        String shortUri;//获取短链接链接
        while(true){
            if(count>10){
                throw new RuntimeException("生成短链接失败，重试次数过多");
            }
            String orginUrl=requestParam.getOriginUrl();//获取原始链接
            orginUrl+=System.currentTimeMillis();//拼接当前时间戳，增加唯一性
            shortUri= HashUtil.hashToBase62(orginUrl);//生成短链接
          if(!bloomFilter.contains(requestParam.getDomain()+"/"+shortUri)){
              break;
          }
          count++;
        }
        return shortUri;
    }
}
