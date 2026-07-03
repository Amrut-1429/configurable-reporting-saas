package com.reporting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReportingApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReportingApplication.class, args);
	}

	@org.springframework.context.annotation.Bean
	public org.springframework.boot.CommandLineRunner syncSequences(
			org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
			com.reporting.repository.ReportTemplateRepository templateRepository) {
		return args -> {
			try {
				jdbcTemplate.execute("SELECT setval('column_mapping_seq', (SELECT coalesce(max(id), 0) + 100 FROM column_mappings), false)");
				jdbcTemplate.execute("SELECT setval('raw_data_seq', (SELECT coalesce(max(id), 0) + 100 FROM raw_excel_data), false)");
				jdbcTemplate.execute("SELECT setval('transaction_seq', (SELECT coalesce(max(id), 0) + 100 FROM transactions), false)");
				// Fix schema migration issues where old NOT NULL columns still exist
				jdbcTemplate.execute("ALTER TABLE IF EXISTS report_templates DROP COLUMN IF EXISTS created_by CASCADE");
				System.out.println("✅ Sequences synchronized successfully!");
			} catch (Exception e) {
				System.out.println("⚠️ Could not sync sequences or alter tables: " + e.getMessage());
			}

			// Seed Templates
			if (templateRepository.count() == 0) {
				templateRepository.save(com.reporting.entity.ReportTemplate.builder()
						.name("Overall Report")
						.reportType("OVERALL")
						.groupByField("OVERALL")
						.metrics(java.util.List.of("SUM_AMOUNT", "SUM_QUANTITY", "COUNT_INVOICES", "SUM_TAX"))
						.columns(java.util.List.of("Total Amount", "Total Quantity", "Invoice Count", "Total Tax"))
						.build());

				templateRepository.save(com.reporting.entity.ReportTemplate.builder()
						.name("Date Wise Report")
						.reportType("DATE")
						.groupByField("DATE")
						.metrics(java.util.List.of("SUM_AMOUNT", "SUM_QUANTITY", "COUNT_INVOICES", "SUM_TAX"))
						.columns(java.util.List.of("Date", "Amount", "Quantity", "Invoice Count", "Tax"))
						.build());

				templateRepository.save(com.reporting.entity.ReportTemplate.builder()
						.name("Station Wise Report")
						.reportType("STATION")
						.groupByField("LOCATION")
						.metrics(java.util.List.of("SUM_AMOUNT", "SUM_QUANTITY", "COUNT_INVOICES", "SUM_TAX"))
						.columns(java.util.List.of("Station", "Amount", "Quantity", "Invoice Count", "Tax"))
						.build());

				templateRepository.save(com.reporting.entity.ReportTemplate.builder()
						.name("Month Wise Report")
						.reportType("MONTH")
						.groupByField("MONTH")
						.metrics(java.util.List.of("SUM_AMOUNT", "SUM_QUANTITY", "COUNT_INVOICES"))
						.columns(java.util.List.of("Month", "Amount", "Quantity", "Invoice Count"))
						.build());

				templateRepository.save(com.reporting.entity.ReportTemplate.builder()
						.name("Product Wise Report")
						.reportType("PRODUCT")
						.groupByField("PRODUCT")
						.metrics(java.util.List.of("SUM_AMOUNT", "SUM_QUANTITY"))
						.columns(java.util.List.of("Product", "Amount", "Quantity"))
						.build());
				
				System.out.println("✅ Seeded Report Templates");
			}
		};
	}
}
