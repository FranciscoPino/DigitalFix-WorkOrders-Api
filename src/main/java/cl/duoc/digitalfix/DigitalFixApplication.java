package cl.duoc.digitalfix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DigitalFixApplication {
    public static void main(String[] args) {
        SpringApplication.run(DigitalFixApplication.class, args);
    }
}
