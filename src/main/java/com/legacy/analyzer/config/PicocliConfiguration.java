package com.legacy.analyzer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import picocli.CommandLine;

@Configuration
public class PicocliConfiguration {
    
    @Bean
    public CommandLine.IFactory picocliFactory() {
        return new SpringCommandLineFactory();
    }
    
    /**
     * Factory personnalisée pour permettre à Picocli d'utiliser Spring pour créer les commandes
     */
    private static class SpringCommandLineFactory implements CommandLine.IFactory {
        private final org.springframework.context.ApplicationContext applicationContext;
        
        public SpringCommandLineFactory() {
            this.applicationContext = SpringContextHolder.getApplicationContext();
        }
        
        @Override
        public <K> K create(Class<K> cls) throws Exception {
            try {
                return applicationContext.getBean(cls);
            } catch (Exception e) {
                // Si Spring ne peut pas créer le bean, utiliser la création par défaut
                return CommandLine.defaultFactory().create(cls);
            }
        }
    }
}