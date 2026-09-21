package com.chunbo.medical.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.entity.DoctorAccount;
import com.chunbo.medical.entity.StaffAccount;
import com.chunbo.medical.mapper.DoctorAccountMapper;
import com.chunbo.medical.mapper.StaffAccountMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 当前登录用户身份解析：从 JwtFilter 放入的 username 属性还原真实姓名。
 * 用于入库人/审核人/盘点人/发药药师/开单医生等单据操作人的真实落库（杜绝写死「张医生」）。
 */
@Service
public class CurrentUserService {

    @Autowired
    private StaffAccountMapper staffAccountMapper;

    @Autowired
    private DoctorAccountMapper doctorAccountMapper;

    /**
     * 解析当前登录人的真实姓名：员工档案 realName > 医生档案 doctorName > 登录账号本身。
     * 未登录（无 request attribute）返回空串，由调用方决定兜底值。
     */
    public String displayName(HttpServletRequest request) {
        String username = request != null && request.getAttribute("username") != null
                ? String.valueOf(request.getAttribute("username")) : null;
        if (username == null || username.isBlank()) return "";
        try {
            StaffAccount sa = staffAccountMapper.selectOne(
                    new LambdaQueryWrapper<StaffAccount>().eq(StaffAccount::getUsername, username));
            if (sa != null && sa.getRealName() != null && !sa.getRealName().isBlank()) return sa.getRealName();
        } catch (Exception ignored) {
        }
        try {
            DoctorAccount da = doctorAccountMapper.selectOne(
                    new LambdaQueryWrapper<DoctorAccount>().eq(DoctorAccount::getUsername, username));
            if (da != null && da.getDoctorName() != null && !da.getDoctorName().isBlank()) return da.getDoctorName();
        } catch (Exception ignored) {
        }
        return username;
    }
}
