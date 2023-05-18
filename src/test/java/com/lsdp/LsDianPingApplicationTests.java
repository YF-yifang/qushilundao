package com.lsdp;

import com.lsdp.dto.LoginFormDTO;
import com.lsdp.dto.Result;
import com.lsdp.entity.Shop;
import com.lsdp.service.IUserService;
import com.lsdp.service.impl.ShopServiceImpl;
import com.lsdp.utils.CacheClient;
import com.lsdp.utils.RedisConstants;
import com.lsdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class LsDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private IUserService userService;

    private final ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }

    @Test
    void testSaveShop() {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }

    /**
     * 生成1000个用户和登录token做并发测试，启动前需要将登录验证的代码注释掉
     */
    @Test
    void testLoginToken() {
        long phone = 13356780000L;
        HttpSession session = new MockHttpSession();
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= 1000; i++) {
            LoginFormDTO loginFormDTO = new LoginFormDTO();
            loginFormDTO.setPhone(phone++ + "");
            Result result = userService.login(loginFormDTO, session);
            String token = result.getData().toString();
            content.append(token).append("\n");
        }
        FileWriter fileWriter = null;
        BufferedWriter bufferedWriter = null;
        try {
            String filePath = "C:/Users/16848/Desktop/java-study/java-project/ls-dianping/src/test/resources/testToken.txt"; // 文件路径和名称
            fileWriter = new FileWriter(filePath);
            bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.write(String.valueOf(content)); // 将字符串写入缓冲区
            bufferedWriter.close(); // 关闭缓冲区，将内容写入文件
            System.out.println("content成功写入文件：\n" + content);
        } catch (IOException e) {
            System.out.println("写入文件时发生错误：" + e.getMessage());
        } finally {
            try {
                assert fileWriter != null;
                fileWriter.close();
                assert bufferedWriter != null;
                bufferedWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    @Test
    void test() {
        System.out.println(LocalDateTime.now().toEpochSecond(ZoneOffset.of("+8")));
        System.out.println(System.currentTimeMillis());
    }
}
