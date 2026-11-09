package ru.netology;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class OrderCardTest {

    private static WebDriver driver;
    private static WebDriverWait wait;
    private static String appUrl;

    @BeforeAll
    static void setup() {
        // URL берём из переменной окружения (для CI), иначе — локальный дефолт
        appUrl = System.getenv("APP_URL") != null
                ? System.getenv("APP_URL")
                : "http://localhost:9999";
        System.out.println("[SETUP] Target URL: " + appUrl);

        // Если путь к драйверу задан через переменную окружения — используем его,
        // иначе полагаемся на Selenium Manager
        String driverPath = System.getenv("WEBDRIVER_CHROME_DRIVER");
        if (driverPath != null && !driverPath.isEmpty()) {
            System.setProperty("webdriver.chrome.driver", driverPath);
            System.out.println("[SETUP] Using driver from env: " + driverPath);
        } else {
            System.out.println("[SETUP] Driver path not set — relying on Selenium Manager.");
        }

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");                               // обязательно для CI
        options.addArguments("--no-sandbox");                                 // для контейнеров
        options.addArguments("--disable-dev-shm-usage");                      // обход лимитов памяти
        options.addArguments("--window-size=1920,1080");                      // иначе в headless мобильная вёрстка
        options.addArguments("--disable-blink-features=AutomationControlled");

        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(60));
        System.out.println("[SETUP] Chrome started successfully in headless mode.");
    }

    @AfterAll
    static void teardown() {
        if (driver != null) {
            driver.quit();
            System.out.println("[TEARDOWN] Browser closed.");
        }
    }

    // ---------- Вспомогательные методы ----------

    private WebElement openPage() {
        System.out.println("[TEST] Opening page: " + appUrl);
        driver.get(appUrl);
        System.out.println("[TEST] Current URL: " + driver.getCurrentUrl());
        System.out.println("[TEST] Page title: " + driver.getTitle());

        return wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("[data-test-id='name'] input")));
    }

    private WebElement getNameField() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("[data-test-id='name'] input")));
    }

    private WebElement getPhoneField() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("[data-test-id='phone'] input")));
    }

    private WebElement checkAgreement() {
        WebElement agreementLabel = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("label[data-test-id='agreement']")));
        agreementLabel.click();
        // Ждём, пока чекбокс реально станет отмеченным
        wait.until(ExpectedConditions.attributeContains(agreementLabel, "class", "checkbox_checked"));
        return agreementLabel;
    }

    private WebElement clickContinue() {
        WebElement submitButton = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[.//span[contains(text(), 'Продолжить')]]")));

        if ("true".equals(submitButton.getAttribute("disabled"))) {
            throw new RuntimeException("Кнопка 'Продолжить' заблокирована!");
        }
        submitButton.click();
        System.out.println("[TEST] Clicked submit button.");
        return submitButton;
    }

    // ---------- Тест 1: happy path ----------

    @Test
    void shouldSubmitCardOrderFormSuccessfully() {
        openPage();

        getNameField().clear();
        getNameField().sendKeys("Иванов Иван Иванович");

        getPhoneField().clear();
        getPhoneField().sendKeys("+79001234567");

        checkAgreement();
        clickContinue();

        // Успешная отправка может обрабатываться дольше — ждём до 90 секунд
        WebDriverWait longWait = new WebDriverWait(driver, Duration.ofSeconds(90));
        WebElement successBlock = longWait.until(
                ExpectedConditions.visibilityOfElementLocated(
                        By.cssSelector("[data-test-id='order-success']")));

        assertTrue(successBlock.isDisplayed(), "Блок успеха не появился!");
        System.out.println("[TEST] ✅ Happy path passed!");
    }

    // ---------- Тест 2: валидация имени (латиница) ----------

    @Test
    void shouldShowValidationErrorWhenNameIsInvalid() {
        WebElement nameInput = openPage();

        System.out.println("[TEST] Вводим НЕКОРРЕКТНЫЕ данные...");
        nameInput.clear();
        nameInput.sendKeys("Ivanov Ivan"); // латиница — недопустима

        checkAgreement();
        clickContinue();

        System.out.println("[TEST] Ждём появления ошибки валидации...");

        boolean errorFound = false;

        // Вариант 1: появился элемент с текстом ошибки
        try {
            WebElement errorTextElement = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-test-id='name'] .input_sub")));
            String errorText = errorTextElement.getText();
            if (!errorText.isEmpty()) {
                System.out.println("   -> Текст ошибки найден: '" + errorText + "'");
                errorFound = true;
            }
        } catch (Exception e) {
            System.out.println("   -> Блок с текстом ошибки не найден.");
        }

        // Вариант 2: у контейнера появился класс input_invalid
        if (!errorFound) {
            try {
                wait.until(ExpectedConditions.attributeContains(
                        By.cssSelector("[data-test-id='name']"), "class", "input_invalid"));
                System.out.println("   -> Класс input_invalid обнаружен!");
                errorFound = true;
            } catch (Exception e) {
                System.out.println("   -> Класс input_invalid не обнаружен.");
            }
        }

        assertTrue(errorFound, "❌ НЕ УДАЛОСЬ НАЙТИ ОШИБКУ ВАЛИДАЦИИ!");
        System.out.println("✅ ТЕСТ ПРОЙДЕН!");
    }
}
