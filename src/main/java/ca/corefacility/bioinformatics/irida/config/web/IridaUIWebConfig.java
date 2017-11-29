package ca.corefacility.bioinformatics.irida.config.web;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.extras.springsecurity4.dialect.SpringSecurityDialect;
import org.thymeleaf.spring4.SpringTemplateEngine;
import org.thymeleaf.spring4.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring4.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;

import ca.corefacility.bioinformatics.irida.config.security.IridaApiSecurityConfig;
import ca.corefacility.bioinformatics.irida.config.services.WebEmailConfig;
import ca.corefacility.bioinformatics.irida.ria.config.AnalyticsHandlerInterceptor;
import ca.corefacility.bioinformatics.irida.ria.config.BreadCrumbInterceptor;
import ca.corefacility.bioinformatics.irida.ria.config.UserSecurityInterceptor;
import ca.corefacility.bioinformatics.irida.ria.web.components.datatables.config.DataTablesRequestResolver;

import com.github.mxab.thymeleaf.extras.dataattribute.dialect.DataAttributeDialect;
import com.google.common.base.Joiner;
import nz.net.ultraq.thymeleaf.LayoutDialect;

/**
 */
@Configuration
@EnableWebMvc
@ComponentScan(basePackages = { "ca.corefacility.bioinformatics.irida.ria" })
@Import({ WebEmailConfig.class, IridaApiSecurityConfig.class })
public class IridaUIWebConfig extends WebMvcConfigurerAdapter implements ApplicationContextAware {
	private static final String SPRING_PROFILE_PRODUCTION = "prod";
	private static final String TEMPLATE_LOCATION = "/pages/";
	private static final String TEMPLATE_SUFFIX = ".html";
	private static final String LOCALE_CHANGE_PARAMETER = "lang";
	private static final long TEMPLATE_CACHE_TTL_MS = 3600000L;
	private static final Logger logger = LoggerFactory.getLogger(IridaUIWebConfig.class);
	private final static String ANALYTICS_DIR = "/etc/irida/analytics/";

	@Autowired
	private Environment env;
	
	@Autowired
	private MessageSource messageSource;

	private ApplicationContext applicationContext;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	public IridaUIWebConfig() {
		super();
	}

	@Bean
	public LocaleChangeInterceptor localeChangeInterceptor() {
		logger.debug("Configuring LocaleChangeInterceptor");
		LocaleChangeInterceptor localeChangeInterceptor = new LocaleChangeInterceptor();
		localeChangeInterceptor.setParamName(LOCALE_CHANGE_PARAMETER);
		return localeChangeInterceptor;
	}

	@Bean
	public AnalyticsHandlerInterceptor analyticsHandlerInterceptor() {
		Path analyticsPath = Paths.get(ANALYTICS_DIR);
		StringBuilder analytics = new StringBuilder();
		if (Files.exists(analyticsPath)) {
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(analyticsPath)) {
				for (Path entry : stream) {
					List<String> lines = Files.readAllLines(entry);
					analytics.append(Joiner.on("\n").join(lines));
					analytics.append("\n");
				}
			} catch (DirectoryIteratorException ex) {
				logger.error("Error reading analytics directory: ", ex);
			} catch (IOException e) {
				logger.error("Error reading analytics file: ", e);
			}
		}
		return new AnalyticsHandlerInterceptor(analytics.toString());
	}

	@Bean
	public UserSecurityInterceptor userSecurityInterceptor() {
		return new UserSecurityInterceptor();
	}

	@Bean(name = "localeResolver")
	public LocaleResolver localeResolver() {
		logger.debug("Configuring LocaleResolver");
		SessionLocaleResolver slr = new SessionLocaleResolver();
		slr.setDefaultLocale(Locale.ENGLISH);
		return slr;
	}

	@Bean
	public BreadCrumbInterceptor breadCrumbInterceptor() {
		return new BreadCrumbInterceptor(messageSource);
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		logger.debug("Configuring Resource Handlers");
		// CSS: default location "/static/styles" during development and
		// production.
		registry.addResourceHandler("/resources/**").addResourceLocations("/resources/");
		registry.addResourceHandler("/public/**").addResourceLocations("/public/");
	}

	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		registry.addViewController("/projects/templates/merge").setViewName("projects/templates/merge");
		registry.addViewController("/projects/templates/copy").setViewName("projects/templates/copy");
		registry.addViewController("/projects/templates/move").setViewName("projects/templates/move");
		registry.addViewController("/projects/templates/remove").setViewName("projects/templates/remove-modal.tmpl");
		registry.addViewController("/cart/templates/galaxy").setViewName("cart/templates/galaxy");
		registry.addViewController("/projects/templates/referenceFiles/delete")
				.setViewName("projects/templates/referenceFiles/delete");
	}


	@Bean
	public SpringResourceTemplateResolver templateResolver(){
		// SpringResourceTemplateResolver automatically integrates with Spring's own
		// resource resolution infrastructure, which is highly recommended.
		SpringResourceTemplateResolver templateResolver = new SpringResourceTemplateResolver();
		templateResolver.setApplicationContext(this.applicationContext);
		templateResolver.setPrefix(TEMPLATE_LOCATION);
		templateResolver.setSuffix(TEMPLATE_SUFFIX);
		// HTML is the default value, added here for the sake of clarity.
		templateResolver.setTemplateMode(TemplateMode.HTML);
		// Template cache is true by default. Set to false if you want
		// templates to be automatically updated when modified.
		templateResolver.setCacheable(true);

		// Set template cache timeout if in production
		// Don't cache at all if in development
		if (env.acceptsProfiles(SPRING_PROFILE_PRODUCTION)) {
			templateResolver.setCacheTTLMs(TEMPLATE_CACHE_TTL_MS);
		} else {
			templateResolver.setCacheTTLMs(0L);
		}
		return templateResolver;
	}

	@Bean
	public SpringTemplateEngine templateEngine(){
		// SpringTemplateEngine automatically applies SpringStandardDialect and
		// enables Spring's own MessageSource message resolution mechanisms.
		SpringTemplateEngine templateEngine = new SpringTemplateEngine();
		templateEngine.setTemplateResolver(templateResolver());
		// Enabling the SpringEL compiler with Spring 4.2.4 or newer can
		// speed up execution in most scenarios, but might be incompatible
		// with specific cases when expressions in one template are reused
		// across different data types, so this flag is "false" by default
		// for safer backwards compatibility.
		templateEngine.setEnableSpringELCompiler(false);
		templateEngine.setAdditionalDialects(additionalDialects());
		return templateEngine;
	}

	@Bean
	public ThymeleafViewResolver viewResolver(){
		ThymeleafViewResolver viewResolver = new ThymeleafViewResolver();
		viewResolver.setTemplateEngine(templateEngine());
		return viewResolver;
	}

	@Override
	public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
		logger.debug("configureDefaultServletHandling");
		configurer.enable();
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		logger.debug("Adding Interceptors to the Registry");
		registry.addInterceptor(localeChangeInterceptor());
		registry.addInterceptor(analyticsHandlerInterceptor());
		registry.addInterceptor(breadCrumbInterceptor());
		registry.addInterceptor(userSecurityInterceptor());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
		argumentResolvers.add(new DataTablesRequestResolver());
	}

	/**
	 * This is to add additional Thymeleaf dialects.
	 * 
	 * @return A Set of Thymeleaf dialects.
	 */
	private Set<IDialect> additionalDialects() {
		Set<IDialect> dialects = new HashSet<>();
		dialects.add(new SpringSecurityDialect());
		dialects.add(new LayoutDialect());
		dialects.add(new DataAttributeDialect());
		return dialects;
	}
}
