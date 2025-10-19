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

package org.springframework.boot.autoconfigure;

import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aot.AotDetector;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.aot.BeanRegistrationExcludeFilter;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.boot.type.classreading.ConcurrentReferenceCachingMetadataReaderFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.ConfigurationClassPostProcessor;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
/**
 * {@link ApplicationContextInitializer} to create a shared
 * {@link CachingMetadataReaderFactory} between the
 * {@link ConfigurationClassPostProcessor} and Spring Boot.
 *
 * @author Phillip Webb
 * @author Dave Syer
 */
class SharedMetadataReaderFactoryContextInitializer implements
		ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered, BeanRegistrationExcludeFilter {
	private static final Log logger = LogFactory.getLog(SharedMetadataReaderFactoryContextInitializer.class);
	public static final String BEAN_NAME = "org.springframework.boot.autoconfigure."
			+ "internalCachingMetadataReaderFactory";

	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		if (AotDetector.useGeneratedArtifacts()) {
			return;
		}
		BeanFactoryPostProcessor postProcessor = new CachingMetadataReaderFactoryPostProcessor(applicationContext);
		logger.info("[SPRING_BOOT] 自定义日志---添加BeanFactoryPostProcessor：CachingMetadataReaderFactoryPostProcessor（作用：优化SpringBoot应用的启动性能，它让SpringBoot在启动时记住已经检查过哪些类，不再做重复工作）");
		applicationContext.addBeanFactoryPostProcessor(postProcessor);
	}

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	public boolean isExcludedFromAotProcessing(RegisteredBean registeredBean) {
		return BEAN_NAME.equals(registeredBean.getBeanName());
	}

	/**
	 * {@link BeanDefinitionRegistryPostProcessor} to register the
	 * {@link CachingMetadataReaderFactory} and configure the
	 * {@link ConfigurationClassPostProcessor}.
	 */
	static class CachingMetadataReaderFactoryPostProcessor
			implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {
		private static final Log logger = LogFactory.getLog(CachingMetadataReaderFactoryPostProcessor.class);
		private final ConfigurableApplicationContext context;

		CachingMetadataReaderFactoryPostProcessor(ConfigurableApplicationContext context) {
			this.context = context;
		}

		@Override
		public int getOrder() {
			// Must happen before the ConfigurationClassPostProcessor is created
			return Ordered.HIGHEST_PRECEDENCE;
		}

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
			logger.info("自定义日志---SharedMetadataReaderFactoryContextInitializer实现BeanFactoryPostProcessor接口，执行方法postProcessBeanFactory：空方法");
		}

		@Override
		public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
			register(registry);
			configureConfigurationClassPostProcessor(registry);
		}

		private void register(BeanDefinitionRegistry registry) {
			if (!registry.containsBeanDefinition(BEAN_NAME)) {
				BeanDefinition definition = BeanDefinitionBuilder
					.rootBeanDefinition(SharedMetadataReaderFactoryBean.class, SharedMetadataReaderFactoryBean::new)
					.getBeanDefinition();
				logger.info("[SPRING_BOOT] 自定义日志---准备注册Bean定义：beanName："+BEAN_NAME);
				registry.registerBeanDefinition(BEAN_NAME, definition);
			}
		}

		private void configureConfigurationClassPostProcessor(BeanDefinitionRegistry registry) {
			try {
				configureConfigurationClassPostProcessor(
						registry.getBeanDefinition(AnnotationConfigUtils.CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME));
			}
			catch (NoSuchBeanDefinitionException ex) {
				// Ignore
			}
		}

		private void configureConfigurationClassPostProcessor(BeanDefinition definition) {
			if (definition instanceof AbstractBeanDefinition abstractBeanDefinition) {
				configureConfigurationClassPostProcessor(abstractBeanDefinition);
				return;
			}
			configureConfigurationClassPostProcessor(definition.getPropertyValues());
		}

		private void configureConfigurationClassPostProcessor(AbstractBeanDefinition definition) {
			Supplier<?> instanceSupplier = definition.getInstanceSupplier();
			if (instanceSupplier != null) {
				definition.setInstanceSupplier(
						new ConfigurationClassPostProcessorCustomizingSupplier(this.context, instanceSupplier));
				return;
			}
			configureConfigurationClassPostProcessor(definition.getPropertyValues());
		}

		private void configureConfigurationClassPostProcessor(MutablePropertyValues propertyValues) {
			logger.info("[SPRING_BOOT] 自定义日志---internalConfigurationAnnotationProcessor设置metadataReaderFactory："+BEAN_NAME);
			propertyValues.add("metadataReaderFactory", new RuntimeBeanReference(BEAN_NAME));
		}

	}

	/**
	 * {@link Supplier} used to customize the {@link ConfigurationClassPostProcessor} when
	 * it's first created.
	 */
	static class ConfigurationClassPostProcessorCustomizingSupplier implements Supplier<Object> {

		private final ConfigurableApplicationContext context;

		private final Supplier<?> instanceSupplier;

		ConfigurationClassPostProcessorCustomizingSupplier(ConfigurableApplicationContext context,
				Supplier<?> instanceSupplier) {
			this.context = context;
			this.instanceSupplier = instanceSupplier;
		}

		@Override
		public Object get() {
			Object instance = this.instanceSupplier.get();
			if (instance instanceof ConfigurationClassPostProcessor postProcessor) {
				configureConfigurationClassPostProcessor(postProcessor);
			}
			return instance;
		}

		private void configureConfigurationClassPostProcessor(ConfigurationClassPostProcessor instance) {
			logger.info("[SPRINGBOOT] 自定义日志---调用getBean："+ BEAN_NAME);
			instance.setMetadataReaderFactory(this.context.getBean(BEAN_NAME, MetadataReaderFactory.class));
		}

	}

	/**
	 * {@link FactoryBean} to create the shared {@link MetadataReaderFactory}.
	 */
	static class SharedMetadataReaderFactoryBean
			implements FactoryBean<ConcurrentReferenceCachingMetadataReaderFactory>, ResourceLoaderAware,
			ApplicationListener<ContextRefreshedEvent> {

		private static final Log logger = LogFactory.getLog(SharedMetadataReaderFactoryBean.class);

		private ConcurrentReferenceCachingMetadataReaderFactory metadataReaderFactory;

		@Override
		public void setResourceLoader(ResourceLoader resourceLoader) {
			this.metadataReaderFactory = new ConcurrentReferenceCachingMetadataReaderFactory(resourceLoader);
		}

		@Override
		public ConcurrentReferenceCachingMetadataReaderFactory getObject() throws Exception {
			return this.metadataReaderFactory;
		}

		@Override
		public Class<?> getObjectType() {
			return CachingMetadataReaderFactory.class;
		}

		@Override
		public boolean isSingleton() {
			return true;
		}

		@Override
		public void onApplicationEvent(ContextRefreshedEvent event) {
			logger.info("[SPRING_BOOT] 自定义日志---【监听事件】：ContextRefreshedEvent，清除缓存，timestamp："+event.getTimestamp());
			this.metadataReaderFactory.clearCache();
		}

	}

}
