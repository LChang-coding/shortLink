package com.nageoffer.shortlink.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;
@Data
@TableName("t_user")
public class UserDO {
        /**
         * ID
         */
        private Long id;

        /**
         * 用户名
         */
        private String username;

        /**
         * 密码
         */
        private String password;

        /**
         * 真实姓名
         */
        private String realName;

        /**
         * 手机号
         */
        private String phone;

        /**
         * 邮箱
         */
        private String mail;

        /**
         * 注销时间戳
         */
        private Long deletionTime;

        /**
         * 创建时间 在新增时自动填充
         */
        @TableField(fill= FieldFill.INSERT)
        private Date createTime;

        /**
         * 修改时间 在新增和修改时自动填充
         */
        @TableField(fill= FieldFill.INSERT_UPDATE)
        private Date updateTime;

        /**
         * 删除标识 0：未删除 1：已删除
         * 在删除时自动填充
         */
        @TableField(fill= FieldFill.INSERT)
        private Integer delFlag;

}
