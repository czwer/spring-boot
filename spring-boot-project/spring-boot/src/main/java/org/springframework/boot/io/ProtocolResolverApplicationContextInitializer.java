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

package org.springframework.boot.io;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ProtocolResolver;
import org.springframework.core.io.support.SpringFactoriesLoader;

/**
 * {@link ApplicationContextInitializer} that adds all {@link ProtocolResolver
 * ProtocolResolvers} registered in a {@code spring.factories} file.
 *
 * @author Scott Frederick
 */
class ProtocolResolverApplicationContextInitializer
		implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	private static final Logger logger = LoggerFactory.getLogger(ProtocolResolverApplicationContextInitializer.class);

	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		SpringFactoriesLoader loader = SpringFactoriesLoader
			.forDefaultResourceLocation(applicationContext.getClassLoader());
		logger.info("[SPRING_BOOT] 自定义日志---加载类型："+ProtocolResolver.class.getName());
		List<ProtocolResolver> protocolResolvers = loader.load(ProtocolResolver.class);
		protocolResolvers.forEach( s ->logger.info("[SPRING_BOOT] 自定义日志---加载的到的ProtocolResolver："+s.getClass().getName()));
		protocolResolvers.forEach(applicationContext::addProtocolResolver);
	}

}
