package com.example.bowling.config;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class BusinessClockConfig {
 @Bean Clock businessClock(){return Clock.system(ZoneId.of("Asia/Seoul"));}
}
