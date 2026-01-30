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

package com.nageoffer.shortlink.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nageoffer.shortlink.admin.common.convention.result.Result;
import com.nageoffer.shortlink.admin.dto.req.ShortLinkGroupStatsAccessRecordReqDTO;
import com.nageoffer.shortlink.admin.dto.req.ShortLinkStatsAccessRecordReqDTO;
import com.nageoffer.shortlink.admin.dto.req.ShortLinkStatsReqDTO;
import com.nageoffer.shortlink.admin.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import com.nageoffer.shortlink.admin.dto.resp.ShortLinkStatsRespDTO;
import com.nageoffer.shortlink.admin.remote.dto.ShortLinkActualRemoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 短链接监控控制层
 *
 */
@RestController
@RequiredArgsConstructor
public class ShortLinkStatsController {

    private final ShortLinkActualRemoteService shortLinkActualRemoteService;

    /**
     * 访问单个短链接指定时间内监控数据
     */
    @GetMapping("/api/short-link/admin/v1/stats")
    public Result<ShortLinkStatsRespDTO> shortLinkStats(@ModelAttribute ShortLinkStatsReqDTO requestParam,
                                                       @RequestParam(value = "enableStatus", required = false) Integer enableStatus) {
        return shortLinkActualRemoteService.oneShortLinkStats(
                requestParam.getFullShortUrl(),
                requestParam.getGid(),
                enableStatus,
                requestParam.getStartDate(),
                requestParam.getEndDate()
        );
    }

    /**
     * 访问分组短链接指定时间内监控数据
     */
    @GetMapping("/api/short-link/admin/v1/stats/group")
    public Result<ShortLinkStatsRespDTO> groupShortLinkStats(
            @RequestParam("gid") String gid,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate) {
        return shortLinkActualRemoteService.groupShortLinkStats(gid, startDate, endDate);
    }

    /**
     * 访问单个短链接指定时间内访问记录监控数据
     */
    @GetMapping("/api/short-link/admin/v1/stats/access-record")
    public Result<Page<ShortLinkStatsAccessRecordRespDTO>> shortLinkStatsAccessRecord(
            @RequestParam("fullShortUrl") String fullShortUrl,
            @RequestParam("gid") String gid,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam(value = "current", required = false, defaultValue = "1") Long current,
            @RequestParam(value = "size", required = false, defaultValue = "10") Long size,
            @RequestParam(value = "enableStatus", required = false) Integer enableStatus) {
        ShortLinkStatsAccessRecordReqDTO reqDTO = new ShortLinkStatsAccessRecordReqDTO();
        reqDTO.setFullShortUrl(fullShortUrl);
        reqDTO.setGid(gid);
        reqDTO.setStartDate(startDate);
        reqDTO.setEndDate(endDate);
        reqDTO.setCurrent(current);
        reqDTO.setSize(size);
        reqDTO.setEnableStatus(enableStatus);
        return shortLinkActualRemoteService.shortLinkStatsAccessRecord(
                reqDTO.getFullShortUrl(),
                reqDTO.getGid(),
                reqDTO.getStartDate(),
                reqDTO.getEndDate(),
                reqDTO.getEnableStatus(),
                reqDTO.getCurrent(),
                reqDTO.getSize()
        );
    }

    /**
     * 访问分组短链接指定时间内访问记录监控数据
     */
    @GetMapping("/api/short-link/admin/v1/stats/access-record/group")
    public Result<Page<ShortLinkStatsAccessRecordRespDTO>> groupShortLinkStatsAccessRecord(
            @RequestParam("gid") String gid,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam(value = "current", required = false, defaultValue = "1") Long current,
            @RequestParam(value = "size", required = false, defaultValue = "10") Long size) {
        ShortLinkGroupStatsAccessRecordReqDTO reqDTO = new ShortLinkGroupStatsAccessRecordReqDTO();
        reqDTO.setGid(gid);
        reqDTO.setStartDate(startDate);
        reqDTO.setEndDate(endDate);
        reqDTO.setCurrent(current);
        reqDTO.setSize(size);
        return shortLinkActualRemoteService.groupShortLinkStatsAccessRecord(
                reqDTO.getGid(),
                reqDTO.getStartDate(),
                reqDTO.getEndDate(),
                reqDTO.getCurrent(),
                reqDTO.getSize()
        );
    }
}
