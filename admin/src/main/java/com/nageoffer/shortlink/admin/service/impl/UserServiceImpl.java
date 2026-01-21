package com.nageoffer.shortlink.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.UUID;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nageoffer.shortlink.admin.common.biz.user.UserContext;
import com.nageoffer.shortlink.admin.common.convention.exception.ClientException;
import com.nageoffer.shortlink.admin.common.convention.exception.ServiceException;
import com.nageoffer.shortlink.admin.common.enums.UserErrorCodeEnum;
import com.nageoffer.shortlink.admin.dao.entity.UserDO;
import com.nageoffer.shortlink.admin.dao.mapper.UserMapper;
import com.nageoffer.shortlink.admin.dto.req.UserLoginReqDTO;
import com.nageoffer.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.nageoffer.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.nageoffer.shortlink.admin.dto.resp.UserLoginRespDTO;
import com.nageoffer.shortlink.admin.dto.resp.UserRespDTO;
import com.nageoffer.shortlink.admin.service.GroupService;
import com.nageoffer.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.swing.*;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.nageoffer.shortlink.admin.common.constant.RedisCacheConstant.LOCK_USER_REGISTER_KEY;
import static com.nageoffer.shortlink.admin.common.constant.RedisCacheConstant.USER_LOGIN_KEY;
import static com.nageoffer.shortlink.admin.common.enums.UserErrorCodeEnum.*;

/**
 * 用户服务实现层
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper,UserDO> implements UserService {
    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final GroupService groupService;
    @Override
    public UserRespDTO getUserByUsername(String username) {
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username);
        UserDO userDO = baseMapper.selectOne(queryWrapper);
        if (userDO == null) {
            throw new ServiceException(UserErrorCodeEnum.USER_NULL);
        }
        UserRespDTO result = new UserRespDTO();
        BeanUtils.copyProperties(userDO, result);
        return result;
    }

    @Override
    public Boolean hasUsername(String username) {
        return !userRegisterCachePenetrationBloomFilter.contains(username);// 使用布隆过滤器检查用户名是否存在
//        LambdaQueryWrapper<UserDO> eq = Wrappers.lambdaQuery(UserDO.class)
//                .eq(UserDO::getUsername, username);// 构建查询条件：根据用户名进行等值匹配
//        UserDO userDO = baseMapper.selectOne(eq);// 执行查询操作，返回匹配的用户实体
//        if (userDO != null) {
//            return false;
//        }
//        return true;
    }
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void register(UserRegisterReqDTO requestParam) {
        if (!hasUsername(requestParam.getUsername())) {
            throw new ClientException(USER_NAME_EXIST);
        }
        RLock lock = redissonClient.getLock(LOCK_USER_REGISTER_KEY + requestParam.getUsername());//一个用户名一把锁 防止并发注册同一个未注册用户名造成db压力
        if (!lock.tryLock()) {
            throw new ClientException(USER_NAME_EXIST);
        }
        try {
            int inserted = baseMapper.insert(BeanUtil.toBean(requestParam, UserDO.class));
            if (inserted < 1) {
                throw new ClientException(USER_SAVE_ERROR);
            }
            userRegisterCachePenetrationBloomFilter.add(requestParam.getUsername());
            groupService.saveGroup("默认分组", requestParam.getUsername());
        } catch (DuplicateKeyException ex) {
            throw new ClientException(USER_EXIST);
        } finally {
            lock.unlock();
        }

    }
    @Override
    public void update(UserUpdateReqDTO requestParam) {
        if (!Objects.equals(requestParam.getUsername(), UserContext.getUsername())) {
            throw new ClientException("当前登录用户修改请求异常");
        }
        LambdaUpdateWrapper<UserDO> updateWrapper = Wrappers.lambdaUpdate(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername());// 构建更新条件：根据用户名进行等值匹配
        baseMapper.update(BeanUtil.toBean(requestParam, UserDO.class), updateWrapper);
    }

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        // 1. 构建 Lambda 查询条件：根据用户名、密码且未删除(del_flag=0)查询用户
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername())
                .eq(UserDO::getPassword, requestParam.getPassword())
                .eq(UserDO::getDelFlag, 0);

        // 2. 从数据库中获取单个用户信息
        UserDO userDO = baseMapper.selectOne(queryWrapper);

        // 3. 业务异常处理：如果查询结果为空，说明账号密码错误或用户不存在/已注销
        if (userDO == null) {
            throw new ClientException("用户不存在"); // 注意：此处中文在GBK环境下易报错
        }

        /**
         * 4. 检查 Redis 中该用户是否已经登录
         * 结构：Hash | Key: login_用户名 | SubKey: token | Value: 用户信息JSON
         */
        Map<Object, Object> hasLoginMap = stringRedisTemplate.opsForHash().entries(USER_LOGIN_KEY + requestParam.getUsername());

        // 5. 如果 Redis 中已有登录信息（即用户已在某处登录且未过期）
        if (CollUtil.isNotEmpty(hasLoginMap)) {
            // 刷新该登录信息的过期时间（续期 30 分钟）
            stringRedisTemplate.expire(USER_LOGIN_KEY + requestParam.getUsername(), 30L, TimeUnit.DAYS);

            // 从 Hash 中提取现有的 Token (uuid)
            /**
             * 5.2 从 Map 的键集中提取现有的 Token：
             * - hasLoginMap.keySet().stream(): 将所有键转为流
             * - findFirst(): 获取第一个键（即已存在的 UUID）
             * - map(Object::toString): 将对象类型安全转换为 String
             * - orElseThrow: 若流为空（极端异常情况）则抛出错误
             */
            String token = hasLoginMap.keySet().stream()
                    .findFirst()
                    .map(Object::toString)
                    .orElseThrow(() -> new ClientException("用户登录错误"));

            // 直接返回已存在的 Token，实现“单点登录/复用登录”的效果
            return new UserLoginRespDTO(token);
        }

        /**
         * 6. 首次登录逻辑（或登录已过期）
         */
        // 生成一个唯一的随机字符串作为 Token
        String uuid = UUID.randomUUID().toString();

        // 将用户信息序列化为 JSON 字符串并存入 Redis Hash 中
        // 这里使用了 FastJSON 的静态方法 toJSONString
        stringRedisTemplate.opsForHash().put(
                USER_LOGIN_KEY + requestParam.getUsername(),
                uuid,
                JSON.toJSONString(userDO)
        );

        // 设置该 Hash 结构的过期时间为 30 分钟
        stringRedisTemplate.expire(USER_LOGIN_KEY + requestParam.getUsername(), 30L, TimeUnit.MINUTES);

        // 返回新生成的 Token
        return new UserLoginRespDTO(uuid);
    }
    @Override
    public Boolean checkLogin(String username, String token) {
        return stringRedisTemplate.opsForHash().get(USER_LOGIN_KEY + username, token) != null;// 检查 Redis 中是否存在对应的用户登录信息
    }
    @Override
    public void logout(String username, String token) {
        if (checkLogin(username, token)) {
            stringRedisTemplate.delete(USER_LOGIN_KEY + username);
            return;
        }
        throw new ClientException("用户Token不存在或用户未登录");
    }
}
