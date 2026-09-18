package com.chunbo.medical.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_doctor_account")
public class DoctorAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String doctorName;
    private String doctorId;
    private String department;
    private String title;
    private String qualificationNo;
    private String phone;
    private String status;
    private LocalDateTime createTime;
}