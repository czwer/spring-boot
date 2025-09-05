/*
 * Copyright 2012-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure.ssl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.availability.ApplicationAvailabilityAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.io.ApplicationResourceLoader;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.boot.ssl.SslBundleRegistry;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for SSL.
 *
 * @author Scott Frederick
 * @since 3.1.0
 */
@AutoConfiguration
@EnableConfigurationProperties(SslProperties.class)
public class SslAutoConfiguration {
	private static final Log logger = LogFactory.getLog(SslAutoConfiguration.class);


	private final ResourceLoader resourceLoader;

	private final SslProperties sslProperties;

	SslAutoConfiguration(ResourceLoader resourceLoader, SslProperties sslProperties) {
		this.resourceLoader = ApplicationResourceLoader.get(resourceLoader, true);
		this.sslProperties = sslProperties;
	}

	@Bean
	FileWatcher fileWatcher() {
		logger.info("[SPRING_BOOT] 自定义日志---@Bean方式声明Bean：FileWatcher（）");
		return new FileWatcher(this.sslProperties.getBundle().getWatch().getFile().getQuietPeriod());
	}

	@Bean
	SslPropertiesBundleRegistrar sslPropertiesSslBundleRegistrar(FileWatcher fileWatcher) {
		logger.info("[SPRING_BOOT] 自定义日志---@Bean方式声明Bean：SslPropertiesBundleRegistrar（）");
		return new SslPropertiesBundleRegistrar(this.sslProperties, fileWatcher, this.resourceLoader);
	}

	@Bean
	@ConditionalOnMissingBean({ SslBundleRegistry.class, SslBundles.class })
	DefaultSslBundleRegistry sslBundleRegistry(ObjectProvider<SslBundleRegistrar> sslBundleRegistrars) {
		logger.info("[SPRING_BOOT] 自定义日志---@Bean方式声明Bean：DefaultSslBundleRegistry（）");
		DefaultSslBundleRegistry registry = new DefaultSslBundleRegistry();
		sslBundleRegistrars.orderedStream().forEach((registrar) -> registrar.registerBundles(registry));
		return registry;
	}

}
