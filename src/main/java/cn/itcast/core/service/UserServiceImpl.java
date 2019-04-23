package cn.itcast.core.service;

import cn.itcast.core.dao.user.UserDao;
import cn.itcast.core.pojo.user.User;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import org.apache.activemq.command.ActiveMQQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;

import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.Session;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private JmsTemplate jmsTemplate;

    //定义发送的目标队列
    @Autowired
    private ActiveMQQueue smsDestination;

    @Autowired
    private RedisTemplate redisTemplate;

    //获取common项目中, sms.properties文件中的模板编号
    @Value("${template_code}")
    private String templateCode;

    //获取ommon项目中, sms.properties文件中的签名
    @Value("${sign_name}")
    private String signName;

    @Autowired
    private UserDao userDao;

    @Override
    public void sendCode(final String phone) {
        //1. 生成一个随机6位数字作为验证码内容
        final long code = (long)(Math.random() * 1000000);

        //2. 将手机号作为key, 验证码内容作为value存入redis中, 生存周期为5分钟
        redisTemplate.boundValueOps(phone).set(code, 5, TimeUnit.MINUTES);

        //3. 将手机号, 验证码, 短信模板编号, 短信签名等封装成Map类型数据发送给消息服务器
        jmsTemplate.send(smsDestination, new MessageCreator() {
            @Override
            public Message createMessage(Session session) throws JMSException {
                MapMessage mapMessage = session.createMapMessage();
                mapMessage.setString("mobile", phone);
                mapMessage.setString("templateCode", templateCode);
                mapMessage.setString("signName", signName);
                //封装验证码内容
                Map<String, String> paramMap = new HashMap<>();
                paramMap.put("code", String.valueOf(code));
                //将验证码map转成json格式字符串
                String param = JSON.toJSONString(paramMap);
                mapMessage.setString("param", param);
                return mapMessage;
            }
        });
    }

    @Override
    public Boolean checkSmsCode(String phone, String smsCode) {
        if (phone == null || smsCode == null || "".equals(phone) || "".equals(smsCode)) {
            return false;
        }
        //1. 根据手机号, 到redis中获取我们存入的验证码
        Long redisCode = (Long) redisTemplate.boundValueOps(phone).get();
        if (redisCode == null) {
            return false;
        }
        //2. 对比redis中的验证码和页面传入的验证码是否一致
        if (!smsCode.equals(String.valueOf(redisCode))) {
            return false;
        }
        //3. 返回
        return true;
    }

    @Override
    public void add(User user) {
        //添加的用户默认为Y正常用户
        user.setStatus("Y");
        //用户创建时间
        user.setCreated(new Date());
        //用户更新时间
        user.setUpdated(new Date());
        //添加保存
        userDao.insertSelective(user);
    }

    public static void main(String[] args) {
        long l = (long)(Math.random() * 1000000);
        System.out.println("=====" + l);
        System.out.println("lalal");
    }

}
