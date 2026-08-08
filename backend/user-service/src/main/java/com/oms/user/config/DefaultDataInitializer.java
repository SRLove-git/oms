package com.oms.user.config;

import com.oms.user.entity.MerchantInfo;
import com.oms.user.entity.User;
import com.oms.user.mapper.MerchantInfoMapper;
import com.oms.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次启动时写入演示数据：平台管理员与演示商户（可通过 oms.seed.demo-data=false 关闭）。
 */
@Component
public class DefaultDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultDataInitializer.class);

    private final UserMapper userMapper;
    private final MerchantInfoMapper merchantMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${oms.seed.demo-data:true}")
    private boolean seedDemoData;

    public DefaultDataInitializer(UserMapper userMapper, MerchantInfoMapper merchantMapper) {
        this.userMapper = userMapper;
        this.merchantMapper = merchantMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedDemoData) {
            return;
        }
        if (userMapper.selectCount(null) > 0) {
            return;
        }

        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setRealName("平台管理员");
        admin.setUserType(1);
        admin.setStatus(1);
        userMapper.insert(admin);

        MerchantInfo merchant = new MerchantInfo();
        merchant.setMerchantNo("M00001");
        merchant.setName("演示商户");
        merchant.setContactName("商户管理员");
        merchant.setContactPhone("13800000000");
        merchant.setStatus(2);
        merchantMapper.insert(merchant);

        User merchantUser = new User();
        merchantUser.setUsername("merchant");
        merchantUser.setPassword(passwordEncoder.encode("merchant123"));
        merchantUser.setRealName("商户管理员");
        merchantUser.setUserType(2);
        merchantUser.setMerchantId(merchant.getId());
        merchantUser.setStatus(1);
        userMapper.insert(merchantUser);

        log.info("演示数据已初始化：admin/admin123（平台），merchant/merchant123（商户）");
    }
}
