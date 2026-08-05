package com.testingautomation.testautomation.config.WebDriverConfig;

import org.openqa.selenium.UnexpectedAlertBehaviour;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Component
public class WebDriverFactory {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Value("${selenium.remote-url:}")
    private String remoteUrl;

    @Value("${selenium.headless:false}")
    private boolean headless;

    public WebDriver createDriver() throws MalformedURLException {

        ChromeOptions options = new ChromeOptions();

        options.addArguments("--disable-gpu");
        options.addArguments("--ignore-certificate-errors");

        options.setAcceptInsecureCerts(true);
        options.setUnhandledPromptBehaviour(UnexpectedAlertBehaviour.DISMISS);
        // Disable browser popups
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("profile.default_content_setting_values.notifications", 2);
        prefs.put("profile.default_content_setting_values.geolocation", 2);

        // 🔥 Disable password manager + breach warnings
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);

        // Extra hardening
        prefs.put("profile.password_manager_leak_detection", false);

        options.setExperimentalOption("prefs", prefs);

        // Remove automation warnings
        options.setExperimentalOption("excludeSwitches", Arrays.asList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        if (headless) {
            options.addArguments("--headless=new");
            options.addArguments("--window-size=1920,1080");

            // Helps normalize rendering on different DPI displays
            options.addArguments("--force-device-scale-factor=1");
            options.addArguments("--high-dpi-support=1");
        }

        if ("prod".equals(activeProfile)) {
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");

            WebDriver driver = new RemoteWebDriver(
                    new URL(remoteUrl),
                    options
            );

            // Maximize only when not headless
            if (!headless) {
                driver.manage().window().maximize();
            }

            return driver;
        }

        WebDriver driver = new ChromeDriver(options);

        // Maximize only when not headless
        if (!headless) {
            driver.manage().window().maximize();
        }

        return driver;
    }

//    public WebDriver createDriver() throws MalformedURLException {
//
//        ChromeOptions options = new ChromeOptions();
//
//        if (headless) {
//            options.addArguments("--headless=new");
//        }
//
//        options.addArguments("--disable-gpu");
//        options.addArguments("--window-size=1534,664");
//
//        if ("prod".equals(activeProfile)) {
//            options.addArguments("--no-sandbox");
//            options.addArguments("--disable-dev-shm-usage");
//
//            return new RemoteWebDriver(
//                    new URL(remoteUrl),
//                    options
//            );
//        }
//
//        return new ChromeDriver(options);
//    }
}