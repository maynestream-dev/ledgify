package dev.maynestream.ledgify.account;

import dev.maynestream.ledgify.error.GrpcExceptionAdvice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = {
        "dev.maynestream.ledgify.account",
        "dev.maynestream.ledgify.transaction"
})
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }

    @Bean
    GrpcExceptionAdvice grpcExceptionAdvice() {
        return new GrpcExceptionAdvice();
    }
}

