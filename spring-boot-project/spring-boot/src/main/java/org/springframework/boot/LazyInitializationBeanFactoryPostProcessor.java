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

package org.springframework.boot;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.core.Ordered;

/**
 * {@link BeanFactoryPostProcessor} to set lazy-init on bean definitions that are not
 * {@link LazyInitializationExcludeFilter excluded} and have not already had a value
 * explicitly set.
 * <p>
 * Note that {@link SmartInitializingSingleton SmartInitializingSingletons} are
 * automatically excluded from lazy initialization to ensure that their
 * {@link SmartInitializingSingleton#afterSingletonsInstantiated() callback method} is
 * invoked.
 * <p>
 * Beans that are in the {@link BeanDefinition#ROLE_INFRASTRUCTURE infrastructure role}
 * are automatically excluded from lazy initialization, too.
 *
 * @author Andy Wilkinson
 * @author Madhura Bhave
 * @author Tyler Van Gorder
 * @author Phillip Webb
 * @author Moritz Halbritter
 * @since 2.2.0
 * @see LazyInitializationExcludeFilter
 */
public final class LazyInitializationBeanFactoryPostProcessor implements BeanFactoryPostProcessor, Ordered {
	private static final Log logger = LogFactory.getLog(LazyInitializationBeanFactoryPostProcessor.class);
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		logger.info("[SPRINGBOOT] 自定义日志---LazyInitializationBeanFactoryPostProcessor实现BeanFactoryPostProcessor接口，执行方法postProcessBeanFactory：主要作用是启用全局的延迟初始化（Lazy Initialization）模式，通过配置Bean工厂来延迟所有单例Bean的创建，直到它们第一次被实际使用时才进行初始化");
		Collection<LazyInitializationExcludeFilter> filters = getFilters(beanFactory);
		for (String beanName : beanFactory.getBeanDefinitionNames()) {
			BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
			if (beanDefinition instanceof AbstractBeanDefinition abstractBeanDefinition) {
				postProcess(beanFactory, filters, beanName, abstractBeanDefinition);
			}
		}
	}

	private Collection<LazyInitializationExcludeFilter> getFilters(ConfigurableListableBeanFactory beanFactory) {
		// Take care not to force the eager init of factory beans when getting filters
		ArrayList<LazyInitializationExcludeFilter> filters = new ArrayList<>(
				beanFactory.getBeansOfType(LazyInitializationExcludeFilter.class, false, false).values());
		filters.add(LazyInitializationExcludeFilter.forBeanTypes(SmartInitializingSingleton.class));
		filters.add(new InfrastructureRoleLazyInitializationExcludeFilter());
		return filters;
	}

	private void postProcess(ConfigurableListableBeanFactory beanFactory,
			Collection<LazyInitializationExcludeFilter> filters, String beanName,
			AbstractBeanDefinition beanDefinition) {
		Boolean lazyInit = beanDefinition.getLazyInit();
		if (lazyInit != null) {
			return;
		}
		Class<?> beanType = getBeanType(beanFactory, beanName);
		if (!isExcluded(filters, beanName, beanDefinition, beanType)) {
			beanDefinition.setLazyInit(true);
		}
	}

	private Class<?> getBeanType(ConfigurableListableBeanFactory beanFactory, String beanName) {
		try {
			return beanFactory.getType(beanName, false);
		}
		catch (NoSuchBeanDefinitionException ex) {
			return null;
		}
	}

	private boolean isExcluded(Collection<LazyInitializationExcludeFilter> filters, String beanName,
			AbstractBeanDefinition beanDefinition, Class<?> beanType) {
		if (beanType != null) {
			for (LazyInitializationExcludeFilter filter : filters) {
				if (filter.isExcluded(beanName, beanDefinition, beanType)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	/**
	 * Excludes all {@link BeanDefinition bean definitions} which have the infrastructure
	 * role from lazy initialization.
	 */
	private static final class InfrastructureRoleLazyInitializationExcludeFilter
			implements LazyInitializationExcludeFilter {

		@Override
		public boolean isExcluded(String beanName, BeanDefinition beanDefinition, Class<?> beanType) {
			return beanDefinition.getRole() == BeanDefinition.ROLE_INFRASTRUCTURE;
		}

	}

}
