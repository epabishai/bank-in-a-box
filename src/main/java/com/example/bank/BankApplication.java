package com.example.bank;

import io.dropwizard.core.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.core.server.AbstractServerFactory;
import com.example.bank.errors.ErrorHandling;
import org.flywaydb.core.Flyway;

public class BankApplication extends Application<BankConfiguration> {
    public static void main(String[] args) throws Exception {
        new BankApplication().run(args);
    }

    @Override
    public String getName() {
        return "bank-in-a-box";
    }

    @Override
    public void initialize(Bootstrap<BankConfiguration> bootstrap) {
        bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
                bootstrap.getConfigurationSourceProvider(),
                new EnvironmentVariableSubstitutor(false)
        ));
    }

    @Override
    public void run(BankConfiguration configuration, Environment environment) {
        // Register our providers instead of Dropwizard's competing default mappers.
        if (configuration.getServerFactory() instanceof AbstractServerFactory serverFactory) {
            serverFactory.setRegisterDefaultExceptionMappers(false);
        }
        ErrorHandling.register(environment);
        Flyway.configure()
                .dataSource(
                        configuration.getDatabase().getUrl(),
                        configuration.getDatabase().getUser(),
                        configuration.getDatabase().getPassword()
                )
                .load()
                .migrate();
        environment.jersey().register(new HealthResource());
    }
}
