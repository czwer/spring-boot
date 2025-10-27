/*
 * Copyright 2012-2020 the original author or authors.
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

package org.springframework.boot.logging;

import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link LoggingSystemFactory} that delegates to other factories.
 *
 * @author Phillip Webb
 */
class DelegatingLoggingSystemFactory implements LoggingSystemFactory {
	private static final Logger logger = LoggerFactory.getLogger(DelegatingLoggingSystemFactory.class);
	private final Function<ClassLoader, List<LoggingSystemFactory>> delegates;

	/**
	 * Create a new {@link DelegatingLoggingSystemFactory} instance.
	 * @param delegates a function that provides the delegates
	 */
	DelegatingLoggingSystemFactory(Function<ClassLoader, List<LoggingSystemFactory>> delegates) {
		this.delegates = delegates;
	}

	@Override
	public LoggingSystem getLoggingSystem(ClassLoader classLoader) {
		List<LoggingSystemFactory> delegates = (this.delegates != null) ? this.delegates.apply(classLoader) : null;
		if (delegates != null) {
			delegates.forEach( s ->logger.info("[SPRING_BOOT] 自定义日志---加载的到的LoggingSystemFactory："+s.getClass().getName()));
			for (LoggingSystemFactory delegate : delegates) {
				LoggingSystem loggingSystem = delegate.getLoggingSystem(classLoader);
				if (loggingSystem != null) {
					logger.info("[SPRINGBOOT] 自定义日志---使用的日志系统："+loggingSystem.getClass().getName());
					return loggingSystem;
				}
			}
		}
		return null;
	}

}
