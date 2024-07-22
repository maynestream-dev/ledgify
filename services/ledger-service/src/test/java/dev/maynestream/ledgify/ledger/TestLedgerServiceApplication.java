package dev.maynestream.ledgify.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
public class TestLedgerServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(LedgerServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}
}
