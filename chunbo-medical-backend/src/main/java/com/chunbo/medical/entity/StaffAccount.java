package com.chunbo.medical.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_staff_account")
public class StaffAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String realName;
    private String staffId;
    private String role; // ADMIN, HR, DOCTOR
    private String department;
    private String title;
    private String phone;
    private String status; // ENABLE, DISABLE
    private LocalDateTime createTime;
}
