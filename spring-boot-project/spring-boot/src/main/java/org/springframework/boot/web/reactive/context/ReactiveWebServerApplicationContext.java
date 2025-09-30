/*
 * Copyright 2012-2025 the original author or authors.
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

package org.springframework.boot.web.reactive.context;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.web.context.ConfigurableWebServerApplicationContext;
import org.springframework.boot.web.context.MissingWebServerFactoryBeanException;
import org.springframework.boot.web.context.WebServerGracefulShutdownLifecycle;
import org.springframework.boot.web.reactive.server.ReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.ApplicationContextException;
import org.springframework.core.metrics.StartupStep;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.util.StringUtils;
/**
 * A {@link GenericReactiveWebApplicationContext} that can be used to bootstrap itself
 * from a contained {@link ReactiveWebServerFactory} bean.
 *
 * @author Brian Clozel
 * @since 2.0.0
 */
public class ReactiveWebServerApplicationContext extends GenericReactiveWebApplicationContext
		implements ConfigurableWebServerApplicationContext {
	private static final Log logger = LogFactory.getLog(ReactiveWebServerApplicationContext.class);
	private volatile WebServerManager serverManager;

	private String serverNamespace;

	/**
	 * Create a new {@link ReactiveWebServerApplicationContext}.
	 */
	public ReactiveWebServerApplicationContext() {
	}

	/**
	 * Create a new {@link ReactiveWebServerApplicationContext} with the given
	 * {@code DefaultListableBeanFactory}.
	 * @param beanFactory the DefaultListableBeanFactory instance to use for this context
	 */
	public ReactiveWebServerApplicationContext(DefaultListableBeanFactory beanFactory) {
		super(beanFactory);
	}

	@Override
	public final void refresh() throws BeansException, IllegalStateException {
		logger.info("[SPRING] 自定义日志【重要】---调用ReactiveWebServerApplicationContext.refresh，再间接调用AbstractApplicationContext.refresh方法");
		try {
			super.refresh();
		}
		catch (RuntimeException ex) {
			WebServer webServer = getWebServer();
			if (webServer != null) {
				try {
					webServer.stop();
					webServer.destroy();
				}
				catch (RuntimeException stopOrDestroyEx) {
					ex.addSuppressed(stopOrDestroyEx);
				}
			}
			throw ex;
		}
	}

	@Override
	protected void onRefresh() {
		logger.info("[SPRING_BOOT] 自定义日志---实现AbstractApplicationContext.onRefresh：创建并启动嵌入式响应式Web服务器");
		super.onRefresh();
		try {
			createWebServer();
		}
		catch (Throwable ex) {
			throw new ApplicationContextException("Unable to start reactive web server", ex);
		}
	}

	private void createWebServer() {
		WebServerManager serverManager = this.serverManager;
		if (serverManager == null) {
			StartupStep createWebServer = getApplicationStartup().start("spring.boot.webserver.create");
			String webServerFactoryBeanName = getWebServerFactoryBeanName();
			ReactiveWebServerFactory webServerFactory = getWebServerFactory(webServerFactoryBeanName);
			createWebServer.tag("factory", webServerFactory.getClass().toString());
			boolean lazyInit = getBeanFactory().getBeanDefinition(webServerFactoryBeanName).isLazyInit();
			this.serverManager = new WebServerManager(this, webServerFactory, this::getHttpHandler, lazyInit);
			logger.info("[SPRING_BOOT] 自定义日志---【注册单例Bean】：webServerGracefulShutdown");
			getBeanFactory().registerSingleton("webServerGracefulShutdown",
					new WebServerGracefulShutdownLifecycle(this.serverManager.getWebServer()));
			logger.info("[SPRING_BOOT] 自定义日志---【注册单例Bean】：webServerStartStop");
			getBeanFactory().registerSingleton("webServerStartStop",
					new WebServerStartStopLifecycle(this.serverManager));
			createWebServer.end();
		}
		initPropertySources();
	}

	protected String getWebServerFactoryBeanName() {
		// Use bean names so that we don't consider the hierarchy
		String[] beanNames = getBeanFactory().getBeanNamesForType(ReactiveWebServerFactory.class);
		if (beanNames.length == 0) {
			throw new MissingWebServerFactoryBeanException(getClass(), ReactiveWebServerFactory.class,
					WebApplicationType.REACTIVE);
		}
		if (beanNames.length > 1) {
			throw new ApplicationContextException("Unable to start ReactiveWebApplicationContext due to multiple "
					+ "ReactiveWebServerFactory beans : " + StringUtils.arrayToCommaDelimitedString(beanNames));
		}
		return beanNames[0];
	}

	protected ReactiveWebServerFactory getWebServerFactory(String factoryBeanName) {
		logger.info("[SPRINGBOOT] 自定义日志---调用getBean："+ factoryBeanName);
		return getBeanFactory().getBean(factoryBeanName, ReactiveWebServerFactory.class);
	}

	/**
	 * Return the {@link HttpHandler} that should be used to process the reactive web
	 * server. By default this method searches for a suitable bean in the context itself.
	 * @return a {@link HttpHandler} (never {@code null}
	 */
	protected HttpHandler getHttpHandler() {
		// Use bean names so that we don't consider the hierarchy
		String[] beanNames = getBeanFactory().getBeanNamesForType(HttpHandler.class);
		if (beanNames.length == 0) {
			throw new ApplicationContextException(
					"Unable to start ReactiveWebApplicationContext due to missing HttpHandler bean.");
		}
		if (beanNames.length > 1) {
			throw new ApplicationContextException(
					"Unable to start ReactiveWebApplicationContext due to multiple HttpHandler beans : "
							+ StringUtils.arrayToCommaDelimitedString(beanNames));
		}
		logger.info("[SPRINGBOOT] 自定义日志---调用getBean："+ beanNames[0]);
		return getBeanFactory().getBean(beanNames[0], HttpHandler.class);
	}

	@Override
	protected void doClose() {
		if (isActive()) {
			AvailabilityChangeEvent.publish(this, ReadinessState.REFUSING_TRAFFIC);
		}
		super.doClose();
		WebServer webServer = getWebServer();
		if (webServer != null) {
			webServer.destroy();
		}
	}

	/**
	 * Returns the {@link WebServer} that was created by the context or {@code null} if
	 * the server has not yet been created.
	 * @return the web server
	 */
	@Override
	public WebServer getWebServer() {
		WebServerManager serverManager = this.serverManager;
		return (serverManager != null) ? serverManager.getWebServer() : null;
	}

	@Override
	public String getServerNamespace() {
		return this.serverNamespace;
	}

	@Override
	public void setServerNamespace(String serverNamespace) {
		this.serverNamespace = serverNamespace;
	}

}
