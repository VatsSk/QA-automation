package com.testingautomation.testautomation.config.WebDriverConfig;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;

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

        if (headless) {
            options.addArguments("--headless=new");
        }

        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1534,664");

        if ("prod".equals(activeProfile)) {
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");

            return new RemoteWebDriver(
                    new URL(remoteUrl),
                    options
            );
        }

        return new ChromeDriver(options);
    }
}