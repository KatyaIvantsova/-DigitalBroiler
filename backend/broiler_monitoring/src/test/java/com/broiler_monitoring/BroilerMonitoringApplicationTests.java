package com.broiler_monitoring;

import com.broiler_monitoring.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class BroilerMonitoringApplicationTests extends AbstractIntegrationTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void contextLoads() {
	}

	@Test
	void demoDataIsNotSeededWithoutDemoProfile() {
		Integer demoIncidents = jdbc.queryForObject(
				"SELECT COUNT(*) FROM incidents WHERE code LIKE 'INC-DEMO-%'", Integer.class);
		Integer demoNotifications = jdbc.queryForObject(
				"SELECT COUNT(*) FROM notifications WHERE code LIKE 'NOTIF-DEMO-%'", Integer.class);

		assertThat(demoIncidents).isZero();
		assertThat(demoNotifications).isZero();
	}

}
