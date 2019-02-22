/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.insights;

import org.jboss.resteasy.springboot.ResteasyApplicationBuilder;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.Path;
import javax.ws.rs.core.Application;
import javax.ws.rs.ext.Provider;

/**
 * Builds the servlet mappings by scanning beans.
 *
 * This assumes that the Application's resources and providers are in the same package (or a sub-package).
 * It uses the name of the Application's package to lookup the URI mapping, looking for a key like:
 *
 *     rhsm-conduit.package_uri_mappings.${package_name}=${path}
 *
 * This can be used to map multiple different URIs to different JAX-RS applications if we need to in the
 * future.
 */
public class JaxrsApplicationServletInitializer implements BeanFactoryPostProcessor {

    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
        ConfigurableEnvironment env = beanFactory.getBean(ConfigurableEnvironment.class);
        String[] applicationBeanNames = beanFactory.getBeanNamesForType(Application.class);
        Set<Class<?>> resourceBeanClasses =
            Arrays.stream(beanFactory.getBeanNamesForAnnotation(Path.class))
            .map(beanFactory::getType).collect(Collectors.toSet());
        Set<Class<?>> providerBeanClasses =
            Arrays.stream(beanFactory.getBeanNamesForAnnotation(Provider.class))
            .map(beanFactory::getType).collect(Collectors.toSet());

        for (String applicationBeanName : applicationBeanNames) {
            Class<?> applicationClass = beanFactory.getType(applicationBeanName);
            String applicationClassName = applicationClass.getCanonicalName();
            String packageName = applicationClassName.substring(0, applicationClassName.lastIndexOf("."));
            String propertyName = String.format("rhsm-conduit.package_uri_mappings.%s", packageName);
            String uri = env.getRequiredProperty(propertyName);
            Set<Class<?>> resourceClasses = resourceBeanClasses.stream()
                .filter(clazz -> clazz.getCanonicalName().startsWith(packageName))
                .collect(Collectors.toSet());
            Set<Class<?>> providerClasses = providerBeanClasses.stream()
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
