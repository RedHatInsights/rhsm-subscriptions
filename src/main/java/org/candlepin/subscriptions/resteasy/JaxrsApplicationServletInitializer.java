/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.subscriptions.resteasy;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.ext.Provider;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jboss.resteasy.springboot.ResteasyApplicationBuilder;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Builds the servlet mappings by scanning beans.
 *
 * <p>This assumes that the Application's resources and providers are in the same package (or a
 * sub-package). It uses the name of the Application's package to lookup the URI mapping, looking
 * for a key like:
 *
 * <p>subscriptions.package_uri_mappings.${package_name}=${path}
 *
 * <p>This can be used to map multiple different URIs to different JAX-RS applications if we need to
 * in the future.
 */
public class JaxrsApplicationServletInitializer implements BeanFactoryPostProcessor {

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
    ConfigurableEnvironment env = beanFactory.getBean(ConfigurableEnvironment.class);
    String[] applicationBeanNames = beanFactory.getBeanNamesForType(Application.class);
    Set<Class<?>> resourceBeanClasses =
        Arrays.stream(beanFactory.getBeanNamesForAnnotation(Path.class))
            .map(beanFactory::getType)
            .collect(Collectors.toSet());
    Set<Class<?>> providerBeanClasses =
        Arrays.stream(beanFactory.getBeanNamesForAnnotation(Provider.class))
            .map(beanFactory::getType)
            .collect(Collectors.toSet());

    for (String applicationBeanName : applicationBeanNames) {
      Class<?> applicationClass = beanFactory.getType(applicationBeanName);
      String applicationClassName = Objects.requireNonNull(applicationClass).getCanonicalName();
      String packageName = applicationClassName.substring(0, applicationClassName.lastIndexOf("."));
      String propertyName =
          String.format("rhsm-subscriptions.package_uri_mappings.%s", packageName);
      String uri = env.getRequiredProperty(propertyName);
      Set<Class<?>> resourceClasses =
          resourceBeanClasses.stream()
              .filter(clazz -> clazz.getCanonicalName().startsWith(packageName))
              .collect(Collectors.toSet());
      Set<Class<?>> providerClasses =
          providerBeanClasses.stream()
              .filter(clazz -> clazz.getCanonicalName().startsWith(packageName))
              .collect(Collectors.toSet());

      GenericBeanDefinition applicationServletBean = new GenericBeanDefinition();
      applicationServletBean.setFactoryBeanName(ResteasyApplicationBuilder.BEAN_NAME);
      applicationServletBean.setFactoryMethodName("build");

      ConstructorArgumentValues values = new ConstructorArgumentValues();
      values.addIndexedArgumentValue(0, applicationClass.getName());
      values.addIndexedArgumentValue(1, uri);
      values.addIndexedArgumentValue(2, resourceClasses);
      values.addIndexedArgumentValue(3, providerClasses);
      applicationServletBean.setConstructorArgumentValues(values);

      applicationServletBean.setAutowireCandidate(false);
      applicationServletBean.setScope("singleton");

      registry.registerBeanDefinition(applicationClass.getName(), applicationServletBean);
    }
  }
}
