package com.taf.automation.ui.support;

import com.google.common.base.Function;
import com.taf.automation.ui.support.testng.TestNGBase;
import com.thoughtworks.xstream.XStream;
import datainstiller.data.DataPersistence;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.StandardToStringStyle;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.http.client.utils.URIBuilder;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import ru.yandex.qatools.allure.Allure;
import ru.yandex.qatools.allure.events.MakeAttachmentEvent;
import ui.auto.core.data.DataTypes;
import ui.auto.core.pagecomponent.PageComponent;
import ui.auto.core.utils.AjaxTriggeredAction;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Utilities mainly for UI
 */
public class Utils {
    private static final String DOUBLE_QUOTE = "\"";
    private static final String SINGLE_QUOTE = "'";
    private static final String SEPARATOR = ",";
    private static final ReentrantLock lockContext = new ReentrantLock();

    private Utils() {
        // Prevent initialization of class as all public methods should be static
    }

    /**
     * Uses reflection to check if objects are equal<BR>
     * <BR>
     * <B>Notes:</B><BR>
     * 1) Returns true if both objects are null<BR>
     * 2) Returns false if only one object is null<BR>
     *
     * @param actual        - Object containing the Actual Results
     * @param expected      - Object containing the Expected Results
     * @param excludeFields - Fields that are excluded from the comparison
     * @return true if objects are equal else false
     */
    public static boolean equals(Object actual, Object expected, List<String> excludeFields) {
        // Consider objects to be the same if both are null
        if (actual == null && expected == null) {
            return true;
        }

        // Consider objects to be different if only one is null
        if (actual == null) {
            return false;
        }

        if (expected == null) {
            return false;
        }

        if (actual.getClass() != expected.getClass()) {
            return false;
        }

        return EqualsBuilder.reflectionEquals(actual, expected, excludeFields);
    }

    /**
     * Get Element Timeout in seconds
     *
     * @return Element Timeout in seconds
     */
    private static long getElementTimeout() {
        return TestProperties.getInstance().getElementTimeout();
    }

    /**
     * Get Context with the specified driver set if necessary<BR>
     * <B>Notes: </B>
     * <OL>
     * <LI>
     * This method should only be used if the framework is not being used to launch the GUI tests.
     * (For example, the test does not extend TestNGBaseWithoutListeners or ancestors that will provide a context
     * like in the case a different framework is being used to launch the tests.  However, you still want to use
     * page objects that are part of the framework which require a context.)
     * </LI>
     * <LI>
     * The driver will only be set if the driver in the TestNGBase.context() is null.
     * </LI>
     * <LI>
     * This method ensures that the Utils class will function properly provided the specified driver is not null.
     * </LI>
     * <LI>
     * This method uses a lock to ensure it is thread safe as it sets the driver.
     * </LI>
     * </OL>
     *
     * @param driver - The driver to be set if context does not have an initialized driver
     * @return TestContext
     */
    public static TestContext getContext(WebDriver driver) {
        lockContext.lock();
        try {
            if (TestNGBase.context().getDriver() == null) {
                FieldUtils.writeField(TestNGBase.context(), "driver", driver, true);
            }
        } catch (Exception ex) {
            assertThat("Could not set driver for context due to exception:  " + ex.getMessage(), false);
        } finally {
            lockContext.unlock();
        }

        return TestNGBase.context();
    }

    /**
     * Get WebDriver
     *
     * @return WebDriver
     */
    private static WebDriver getWebDriver() {
        return TestNGBase.context().getDriver();
    }

    /**
     * Get a WebDriver from a WebElement. This is useful in cases where you only have a WebElement but need a
     * WebDriver to do some action.<BR>
     * <B>Note: </B> The method WebDriverUtils.getDriverFromElement sometimes throws an exception as such it is
     * recommended to use this method.<BR>
     *
     * @param element - WebElement to get the WebDriver from
     * @return WebDriver
     */
    public static WebDriver getWebDriver(WebElement element) {
        WebDriver useDriver;
        String error;

        try {
            /*
             * Trick to get real element that can return the WebDriver.
             * Notes:
             * 1) If you use element directly it is a proxy and this cannot be cast to RemoteWebElement
             * 2) If WebElement cannot be bound, then this will generate an exception
             */
            WebElement realElement = element.findElement(By.xpath("."));

            // Get the WebDriver object from the real (bound) WebElement
            useDriver = ((RemoteWebElement) realElement).getWrappedDriver();
            error = "no error";
        } catch (Exception ex) {
            useDriver = null;
            error = ex.getMessage();
        }

        assertThat("Could not get driver from element due to exception:  " + error, useDriver, notNullValue());
        return useDriver;
    }

    /**
     * Wraps the Thread.sleep method
     *
     * @param milliseconds - MilliSeconds to pause for
     */
    public static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method will set value &amp; wait for AJAX event to complete redrawing the DOM. The idea behind this
     * method is: first to trigger an action which will cause AJAX event and then to wait until affected by
     * AJAX element become stale. this means that affected element loses his handler as AJAX renders portion
     * of the DOM where affected element resides.
     *
     * @param component         - Component to set value which triggers AJAX
     * @param affectedComponent - The component that becomes stale
     */
    public static void setValueAndWaitForComponent(final PageComponent component, final PageComponent affectedComponent) {
        AjaxTriggeredAction.waitForAjax(new AjaxTriggeredAction() {
            @Override
            public void doAction() {
                component.setValue();
            }
        }, affectedComponent, getElementTimeout() * 1000);
    }

    /**
     * This method will set value &amp; wait for AJAX event to complete redrawing the DOM. The idea behind this
     * method is: first to trigger an action which will cause AJAX event and then to wait until affected by
     * AJAX element become stale. this means that affected element loses his handler as AJAX renders portion
     * of the DOM where affected element resides.
     *
     * @param component - Component to set value which triggers AJAX &amp; becomes stale
     */
    public static void setValueAndWaitForComponent(final PageComponent component) {
        setValueAndWaitForComponent(component, component);
    }

    /**
     * Wait for select option
     *
     * @param select - Drop Down component
     */
    public static WebElement waitForSelectOption(final PageComponent select) {
        return getWebDriverWait().until(new Function<WebDriver, WebElement>() {
            @Nullable
            @Override
            public WebElement apply(@Nullable WebDriver driver) {
                WebElement optionEl = null;
                try {
                    List<WebElement> options = select.findElements(By.tagName("option"));
                    if (options.size() > 0) {
                        for (WebElement option : options) {
                            if (option.getText().trim().equals(select.getData())) {
                                optionEl = option;
                            }
                        }
                    }
                } catch (Exception e) {
                    /* Do nothing */
                }
                return optionEl;
            }
        });
    }

    /**
     * Wait for Element Visibility
     *
     * @param element   - Element
     * @param toDisplay - true to wait for element to be displayed, false to wait for element to be hidden
     */
    private static void waitForElementVisibility(final WebElement element, final boolean toDisplay) {
        new WebDriverWait(getWebDriver(), getElementTimeout()).until(
                driver -> {
                    boolean isDisplayed;
                    try {
                        isDisplayed = element.isDisplayed();
                    } catch (Exception e) {
                        isDisplayed = false;
                    }

                    return isDisplayed == toDisplay;
                });
    }

    /**
     * Wait for the element to be hidden<BR>
     * <BR>
     * <B>Note:</B> Should not be used for PageComponent fields declared in PageObject<BR>
     *
     * @param element - Element
     */
    public static void waitForElementToHide(final WebElement element) {
        waitForElementVisibility(element, false);
    }

    /**
     * Wait for the element to be removed from the DOM<BR>
     * <BR>
     * <B>Note:</B> Should not be used for PageComponent fields declared in PageObject<BR>
     *
     * @param element - Element
     */
    public static void waitForElementToBeRemovedFromDom(WebElement element) {
        if (element == null) {
            return;
        }

        long timeOut = System.currentTimeMillis() + getElementTimeout() * 1000;
        do {
            try {
                element.isDisplayed(); // Should throw exception if element has invalid handler
            } catch (Exception e) {
                return;
            }
        }
        while (System.currentTimeMillis() < timeOut);

        throw new RuntimeException("Web element '" + element + "' was not removed from dom!");
    }

    /**
     * Get WebDriverWait configured with element timeout and poll interval of 100ms
     *
     * @return WebDriverWait
     */
    public static WebDriverWait getWebDriverWait() {
        return new WebDriverWait(getWebDriver(), getElementTimeout(), 100L);
    }

    /**
     * Wait for element to be hidden
     *
     * @param locator - By Locator
     * @return true if element is hidden before timeout else false
     */
    public static boolean waitForElementToHide(By locator) {
        return getWebDriverWait().until(ExpectedConditions.invisibilityOfElementLocated(locator));
    }

    /**
     * Wait for element to be shown
     *
     * @param locator - By Locator
     * @return true if element is shown before timeout else false
     */
    public static WebElement waitForElementToShow(By locator) {
        return getWebDriverWait().until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    /**
     * Wait for element to be in the DOM
     *
     * @param locator - By Locator
     * @return true if element is in the DOM before timeout else false
     */
    public static WebElement waitForElementToBeInDOM(By locator) {
        return getWebDriverWait().until(ExpectedConditions.presenceOfElementLocated(locator));
    }

    /**
     * Wait for element to have text
     *
     * @param element - Element
     * @param text    - Expected Text
     * @return true if element has expected text before timeout else false
     */
    public static boolean waitForElementToHaveText(WebElement element, String text) {
        return getWebDriverWait().until(ExpectedConditions.textToBePresentInElement(element, text));
    }

    /**
     * Waits for the expected number of windows<BR>
     * <B>Notes:</B><BR>
     * In general, the user needs to have performed an action that should trigger another window<BR>
     *
     * @param expectedNumberOfWindows - Expected Number Of Windows
     * @return true if there becomes the expected number of windows else false
     */
    public static boolean waitForNumberOfWindowsToBe(int expectedNumberOfWindows) {
        try {
            return getWebDriverWait().until(ExpectedConditions.numberOfWindowsToBe(expectedNumberOfWindows));
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Attach Data Set to the allure report
     *
     * @param data - Data to be attached to the report
     * @param path - Path to resource
     */
    public static void attachDataSet(DataPersistence data, String path) {
        byte[] attachment = data.toXML().getBytes();
        String name = null;
        if (path != null) {
            try {
                // The path comes from the data-set file which should always use slash
                name = "..." + path.substring(path.lastIndexOf("/"));
            } catch (Exception ex) {
                // Fallback if parsing fails
                name = path;
            }
        }

        MakeAttachmentEvent ev = new MakeAttachmentEvent(attachment, name, "text/xml");
        Allure.LIFECYCLE.fire(ev);
    }

    /**
     * Get Page Width
     *
     * @return Page Width
     */
    public static long getPageWidth() {
        return getWebDriver().manage().window().getSize().width;
    }

    /**
     * Checks if the component is displayed
     *
     * @param component - Component
     * @return true if element is displayed else false
     */
    public static boolean isDisplayed(PageComponent component) {
        By by = component.getLocator();
        if (by == null) {
            throw new RuntimeException("Given component doesn't have locator!");
        }

        List<WebElement> elements = getWebDriver().findElements(by);
        return !elements.isEmpty() && elements.get(0).isDisplayed();
    }

    /**
     * Find a new window handle
     *
     * @param exclusions - Handles that are to be excluded
     * @return null if cannot find a handle not in exclusions else a new window handle
     */
    public static String findNewWindowHandle(Set<String> exclusions) {
        // Get the current window handles
        Set<String> current = getWebDriver().getWindowHandles();

        // Remove all the excluded window handles
        current.removeAll(exclusions);

        if (current.isEmpty()) {
            // There are no new handles
            return null;
        } else {
            // Pick any remaining handle to be returned
            return current.iterator().next();
        }
    }

    /**
     * Reads any text file and returns it as a string<BR>
     * <BR>
     * <B>Notes:</B><BR>
     * 1) File must be a plain text file like a JavaScript file or SQL file for the string to be usable<BR>
     *
     * @param sTextFile - Text File to be read
     * @return String
     */
    public static String readFile(String sTextFile) {
        TextFileReader reader = new TextFileReader();
        reader.setFile(sTextFile);
        reader.readFile();
        return reader.getText();
    }

    /**
     * Reads any resource text file and returns it as a string<BR>
     * <BR>
     * <B>Notes:</B><BR>
     * 1) File must be a plain text file like a JavaScript file or SQL file for the string to be usable<BR>
     * 2) Must be in the compiled jar file<BR>
     *
     * @param sTextFile - Text File to be read
     * @return String
     */
    public static String readResource(String sTextFile) {
        TextFileReader reader = new TextFileReader();
        reader.setFile(sTextFile);
        reader.readFromResource();
        return reader.getText();
    }

    /**
     * Deep Copy using xstream
     *
     * @param obj - Object to deep copy
     * @param <T> - Type
     * @return Deep Copy of Object
     */
    @SuppressWarnings("unchecked")
    public static <T> T deepCopy(T obj) {
        XStream xstream = new XStream();
        String xml = xstream.toXML(obj);
        return (T) xstream.fromXML(xml);
    }

    /**
     * Write (Set) Field using reflection
     *
     * @param target    - Target object that contains the field to be set
     * @param fieldname - Field Name to be set
     * @param value     - Value object to be set
     * @throws RuntimeException - if any exception occurs
     */
    public static void writeField(Object target, String fieldname, Object value) {
        try {
            boolean forceAccess = true;
            FieldUtils.writeField(target, fieldname, value, forceAccess);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Convert the item to an object to be used in verification. Normally, this removes (or sets) fields
     * that are not to be verified (using equals but may be verified separately or in a different manner.)
     *
     * @param converter - XStream configured to do the conversion
     * @param item      - Item to be converted
     * @param <T>       - Object
     * @return Deep Copy of item with only revelant data
     */
    @SuppressWarnings("unchecked")
    public static <T> T convert(XStream converter, T item) {
        return (T) converter.fromXML(converter.toXML(item));
    }

    /**
     * Converts an Unicode code point to a String
     *
     * @param codePoint - Unicode code point to be converted to a String
     * @return String
     */
    public static String toString(int codePoint) {
        return new String(new int[]{codePoint}, 0, 1);
    }

    /**
     * Converts an array of Unicode code points to a String
     *
     * @param codePoints - Array of Unicode code points to be converted to a String
     * @return empty string if code points array is null else string that contains all the code points
     */
    public static String toString(int[] codePoints) {
        if (codePoints == null)
            return "";

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < codePoints.length; i++) {
            builder.append(toString(codePoints[i]));
        }

        return builder.toString();
    }

    /**
     * Splits the specified value into pieces<BR>
     * <B>Notes:</B><BR>
     * 1) If value is null, then array of size 0 is returned<BR>
     *
     * @param value   - Value to be split into an array
     * @param splitOn - The delimiting string
     * @return array of size 0 if value is null else array of at least specified size
     */
    public static String[] splitData(String value, String splitOn) {
        return splitData(value, splitOn, -1);
    }

    /**
     * Splits the specified value into pieces<BR>
     * <B>Notes:</B><BR>
     * 1) If value is null, then array of size 0 is returned<BR>
     * 2) The placeholder for null is set to the string "null"<BR>
     * 3) To reach min array size, empty items are added to the array<BR>
     *
     * @param value   - Value to be split into an array
     * @param splitOn - The delimiting string
     * @param minSize - Min size of array to be returned (-1 to skip)
     * @return array of size 0 if value is null else array of at least specified size
     */
    public static String[] splitData(String value, String splitOn, int minSize) {
        return splitData(value, splitOn, minSize, "null");
    }

    /**
     * Splits the specified value into pieces<BR>
     * <B>Notes:</B><BR>
     * 1) If value is null, then array of size 0 is returned<BR>
     * 2) If nullValue is null, then replacing null values is skipped<BR>
     * 3) To reach min array size, empty items are added to the array<BR>
     *
     * @param value     - Value to be split into an array
     * @param splitOn   - The delimiting string
     * @param minSize   - Min size of array to be returned (-1 to skip)
     * @param nullValue - Placeholder value that indicates null
     * @return array of size 0 if value is null else array of at least specified size
     */
    public static String[] splitData(String value, String splitOn, int minSize, String nullValue) {
        int size = (minSize < 0) ? 0 : minSize;
        if (value == null) {
            return new String[0];
        }

        // Split the data in pieces
        String[] data = StringUtils.splitByWholeSeparatorPreserveAllTokens(value, splitOn);

        // Replace all 'null' placeholders with actual null value
        if (nullValue != null) {
            for (int i = 0; i < data.length; i++) {
                if (data[i].trim().equalsIgnoreCase(nullValue)) {
                    data[i] = null;
                }
            }
        }

        // Ensure that list is min size specfied
        for (int i = data.length; i < size; i++) {
            data = ArrayUtils.add(data, "");
        }

        return data;
    }

    /**
     * Convert currency amount from a String to BigDecimal<BR>
     * <B>Notes:</B>
     * 1) The locale determines what is the decimal separator and the group separator<BR>
     *
     * @param amount - Currency amount as a String
     * @param locale - Locale.CANADA for English or Locale.CANADA_FRENCH for French currency format
     * @return BigDecimal version of the currency amount
     */
    public static BigDecimal parse(final String amount, final Locale locale) {
        final NumberFormat format = NumberFormat.getNumberInstance(locale);
        if (format instanceof DecimalFormat) {
            ((DecimalFormat) format).setParseBigDecimal(true);
        }

        // Clean the amount of all non-digits/periods/commas
        String cleanedAmount = amount.replaceAll("[^\\d.,]", "");
        try {
            return (BigDecimal) format.parse(cleanedAmount);
        } catch (ParseException exception) {
            assertThat("Unable to parse value to BigDecimal:  " + cleanedAmount, false);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Convert currency amount from a String to BigDecimal<BR>
     * <B>Notes:</B>
     * 1) The locale determines what is the decimal separator and the group separator<BR>
     *
     * @param amount         - Currency amount as a String
     * @param locale         - Locale.CANADA for English or Locale.CANADA_FRENCH for French currency format
     * @param defineInfinite - true to define infinite as null
     * @return null if defineInfinite is true and amount is null or empty string, else BigDecimal version of the currency amount
     */
    public static BigDecimal parse(final String amount, final Locale locale, final boolean defineInfinite) {
        if (defineInfinite && StringUtils.defaultString(amount).equals("")) {
            return null;
        }

        return parse(amount, locale);
    }

    /**
     * Compare values handling null values<BR>
     * <B>Notes:</B><BR>
     * 1) null is considered to be infinite<BR>
     *
     * @param lhs - Left Hand Side value
     * @param rhs - Right Hand Side value
     * @return 0 if lhs equals rhs, 1 if lhs greater than rhs, -1 if lhs less than rhs
     */
    public static int compareTo(BigDecimal lhs, BigDecimal rhs) {
        // If both values infinite, then we consider them equal
        if (lhs == null && rhs == null) {
            return 0;
        }

        // If only the lhs is infinite, then it is greater than the rhs
        if (lhs == null) {
            return 1;
        }

        // If only the rhs is infinite, then lhs is less than the rhs
        if (rhs == null) {
            return -1;
        }

        return lhs.compareTo(rhs);
    }

    /**
     * Trim all non-null items
     *
     * @param items - Updates all items by trimming
     */
    public static void trim(String[] items) {
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) {
                items[i] = items[i].trim();
            }
        }
    }

    /**
     * Click the element when it is ready
     *
     * @param element - Element to click
     * @return WebElement that was clicked
     */
    public static WebElement clickWhenReady(WebElement element) {
        getWebDriverWait().until(ExpectedConditionsUtil.ready(element));
        element.click();
        return element;
    }

    /**
     * Click the element when it is ready
     *
     * @param locator - Locator to find element
     * @return WebElement that was clicked
     */
    public static WebElement clickWhenReady(By locator) {
        WebElement element = getWebDriverWait().until(ExpectedConditionsUtil.ready(locator));
        element.click();
        return element;
    }

    /**
     * Click the relative element from anchor element when it is ready
     *
     * @param anchor   - The anchor element from which the element is located from
     * @param relative - Used to find the relative element
     * @return WebElement that was clicked
     */
    public static WebElement clickWhenReady(WebElement anchor, By relative) {
        WebElement element = getWebDriverWait().until(ExpectedConditionsUtil.ready(anchor, relative));
        element.click();
        return element;
    }

    /**
     * Changes the current host URL to the test environment host URL and reloads page<BR>
     * <B>Note: </B> Only use this if a link is hardcoded to a specific host URL but it exists in the current environment<BR>
     */
    public static void changeToCurrentEnvironment() {
        try {
            WebDriver driver = getWebDriver();
            URIBuilder envURI = new URIBuilder(TestProperties.getInstance().getURL());
            URIBuilder uri = new URIBuilder(driver.getCurrentUrl());
            uri.setHost(envURI.getHost());
            driver.get(uri.toString());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Find element using component locator and wait for it to be ready, click and wait for the element to be stale
     *
     * @param component - Use Component's locator to find element to click and wait for it to become stale
     */
    public static void clickAndWaitForStale(PageComponent component) {
        clickAndWaitForStale(component.getLocator());
    }

    /**
     * Find element using locator and wait for it to be ready, click and wait for the element to be stale
     *
     * @param locator - Locator to find element to click and wait for it to become stale
     */
    public static void clickAndWaitForStale(By locator) {
        WebElement element = getWebDriverWait().until(ExpectedConditions.elementToBeClickable(locator));
        clickAndWaitForStale(element);
    }

    /**
     * Click and wait for the element to be stale
     *
     * @param element - Element to click and wait for it to become stale
     */
    public static void clickAndWaitForStale(WebElement element) {
        element.click();
        getWebDriverWait().until(ExpectedConditions.stalenessOf(element));
    }

    /**
     * Find element using component locator and wait for it to be ready,
     * click and wait for the element to be invisible or not in the DOM
     *
     * @param component - Use Component's locator to find element to click and wait for it to become invisible
     */
    public static void clickAndWaitForInvisible(PageComponent component) {
        clickAndWaitForInvisible(component.getLocator());
    }

    /**
     * Find element using locator and wait for it to be ready,
     * click and wait for the element to be invisible or not in the DOM
     *
     * @param locator - Locator to find element to click and wait for it to become invisible
     */
    public static void clickAndWaitForInvisible(By locator) {
        getWebDriverWait().until(ExpectedConditions.elementToBeClickable(locator)).click();
        getWebDriverWait().until(ExpectedConditions.invisibilityOfElementLocated(locator));
    }

    /**
     * Get an element that can become stale<BR>
     * <B>Notes:</B><BR>
     * 1) In the framework by default, we cannot get a stale element exception because every use of an element will
     * always attempt to get a fresh element if this error occurs.  In normal situations, this is fine but there
     * are times when you want to test for staleness/freshness of an element.<BR>
     * 2) If the element is instantiated manually, then this method is not necessary<BR>
     *
     * @param element - An element from a page object that cannot become stale
     * @return WebElement that can become stale
     */
    public static WebElement getUnProxiedElement(WebElement element) {
        return getWebDriverWait().until(ExpectedConditions.presenceOfNestedElementLocatedBy(element, By.xpath(".")));
    }

    /**
     * Get URL Host (ex. www.tsn.ca)
     *
     * @return null if any exception else Box Host
     */
    public static String getHost() {
        try {
            URIBuilder uri = new URIBuilder(TestProperties.getInstance().getURL());
            return uri.getHost();
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Based on browser being used for test return if the driver implementation supports deleting cookies
     *
     * @return true if driver implementation is known to support deleting cookies else false
     */
    public static boolean isCleanCookiesSupported() {
        WebDriverTypeEnum browser = TestProperties.getInstance().getBrowserType();

        if (browser == WebDriverTypeEnum.FIREFOX || browser == WebDriverTypeEnum.CHROME) {
            return true;
        }

        return false;
    }

    /**
     * Determines if the component's data is not null or not empty
     *
     * @param component - Page Component to check
     * @return true if component is not null &amp; component's data is not null or not empty
     */
    public static boolean isNotEmpty(PageComponent component) {
        return component != null && StringUtils.isNotEmpty(component.getData(DataTypes.Data, true));
    }

    /**
     * Determines if the component's data is not null or not empty or not whitespace only
     *
     * @param component - Page Component to check
     * @return true if component is not null &amp; component's data is not null or not empty or not whitespace only
     */
    public static boolean isNotBlank(PageComponent component) {
        return component != null && StringUtils.isNotBlank(component.getData(DataTypes.Data, true));
    }

    /**
     * This method dismiss Alert on the page
     *
     * @return alert text or null
     */
    public static String dismissAlertIfPresent(WebDriver driver) {
        try {
            Alert alert = driver.switchTo().alert();
            String alertText = alert.getText();
            alert.dismiss();
            return alertText;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get RetryPolicy that will retry on any exception or assertion. <BR>
     * <B>Notes: </B> <BR>
     * 1)  The number of retries is from the TestProperties class<BR>
     * 2)  The RetryPolicy can then be used with 'Failsafe.with' to retry a specific method<BR>
     *
     * @return RetryPolicy
     */
    public static RetryPolicy getRetryPolicy() {
        return new RetryPolicy()
                .retryOn(Exception.class, AssertionError.class)
                .withMaxRetries(TestProperties.getInstance().getTestDefaultRetry())
                .withDelay(1, TimeUnit.SECONDS);
    }

    /**
     * Get RetryPolicy that will retry on any exception or assertion to the max specified<BR>
     * <B>Notes: </B><BR>
     * 1)  Useful shortcut if you don't want to use the number of retries is from the TestProperties class<BR>
     *
     * @param maxRetries - Max Retries
     * @return RetryPolicy
     */
    public static RetryPolicy getRetryPolicy(int maxRetries) {
        return getRetryPolicy().withMaxRetries(maxRetries);
    }

    /**
     * Get RetryPolicy that will retry <B>only once</B> on any exception or assertion
     *
     * @return RetryPolicy
     */
    public static RetryPolicy getRetryOncePolicy() {
        return getRetryPolicy(1);
    }

    /**
     * Get RetryPolicy that will retry on any exception or assertion
     * <B>Notes: </B> <BR>
     * 1)  The number of retries is the max of 1 &amp; the retries from the TestProperties class<BR>
     *
     * @return RetryPolicy
     */
    public static RetryPolicy getRetryAtleastOncePolicy() {
        int retries = Math.max(1, TestProperties.getInstance().getTestDefaultRetry());
        return getRetryPolicy(retries);
    }

    /**
     * Get Polling RetryPolicy that can be used to poll for a specific condition<BR>
     * <B>Notes: </B> <BR>
     * 1)  The max duration is from the TestProperties class<BR>
     * 2)  The RetryPolicy can then be used with 'Failsafe.with' to poll for a specific condition<BR>
     *
     * @return RetryPolicy
     */
    public static RetryPolicy getPollingRetryPolicy() {
        return new RetryPolicy()
                .retryOn(Exception.class, AssertionError.class)
                .withMaxDuration(TestProperties.getInstance().getElementTimeout(), TimeUnit.SECONDS)
                .withDelay(1, TimeUnit.SECONDS);
    }

    /**
     * Get the default style for logging for use with ToStringBuilder.reflectionToString
     *
     * @return StandardToStringStyle
     */
    public static StandardToStringStyle getDefaultStyle() {
        StandardToStringStyle style = new StandardToStringStyle();
        style.setUseIdentityHashCode(false);
        style.setUseShortClassName(true);
        return style;
    }

    /**
     * This method will wait for JavaScript to be attached to the DOM.  If the check fails, then the Selenium exception
     * will be caught and instead a user friendly assertion error message will be displayed.<BR>
     * <BR>
     * <B>Notes:</B> Use this method if clicking links/buttons when they are ready still intermittently (or always)
     * do nothing.<BR>
     *
     * @param javascript - JavaScript that indicates the information is in the DOM
     * @param error      - Assertion error message if failure occurs
     */
    public static void ensureJavaScriptAttached(String javascript, String error) {
        boolean expectedJavaScriptAttached;

        try {
            Utils.getWebDriverWait().until(ExpectedConditionsUtil.jsReturnsResult(javascript));
            expectedJavaScriptAttached = true;
        } catch (Exception ex) {
            expectedJavaScriptAttached = false;
        }

        assertThat(error, expectedJavaScriptAttached);
    }

    /**
     * Initialize component<BR>
     * <B>Notes:</B>The main purpose of this method is to initialize a component which is failing to initialize due to
     * a JavascriptException (when binding the core element.)  This problem seems isolated to the GeckoDriver (0.17)
     * which throws this exception instead of StaleElementReferenceException (or just returning false) in certain cases
     * when checking if the element is displayed during initialization of the component.<BR>
     *
     * @param component - Component to be initialized
     * @return true if component initialized successfully else false
     */
    public static boolean initComponent(PageComponent component) {
        WebElement core = Utils.getWebDriverWait().until(ExpectedConditions.visibilityOfElementLocated(component.getLocator()));
        try {
            Method m = PageComponent.class.getDeclaredMethod("initComponent", WebElement.class);
            m.setAccessible(true);
            m.invoke(component, core);
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }

        return true;
    }

    /**
     * Construct an xpath safe value<BR>
     * <B>Note: </B> Insert this value without escaping into the xpath<BR>
     *
     * @param unsafeValue - Value to ensure is safe
     * @return the equivalent unsafe value constructed using the xpath function concat
     */
    public static String constructXpathSafeValue(String unsafeValue) {
        if (StringUtils.defaultString(unsafeValue).equals("")) {
            return "concat('', '')";
        }

        // If no problem quotes, then it is simple to make it safe with concat
        if (!unsafeValue.contains(SINGLE_QUOTE) && !unsafeValue.contains(DOUBLE_QUOTE)) {
            return "concat('" + unsafeValue + "', '')";
        }

        // Store the safe string as it is constructed
        String safe = "concat(";

        String afterPart = unsafeValue;
        int splitPos = -1;
        do {
            int indexOfSingleQuote = StringUtils.indexOf(afterPart, SINGLE_QUOTE);
            int indexOfDoubleQuote = StringUtils.indexOf(afterPart, DOUBLE_QUOTE);

            final String outerQuoteToUse;
            final String problemQuote = findFirstProblemQuote(indexOfSingleQuote, indexOfDoubleQuote);
            if (StringUtils.defaultString(problemQuote).equals(SINGLE_QUOTE)) {
                // First found problem quote
                splitPos = indexOfSingleQuote;

                // The character to surround the problem quote with
                outerQuoteToUse = DOUBLE_QUOTE;
            } else if (StringUtils.defaultString(problemQuote).equals(DOUBLE_QUOTE)) {
                // First found problem quote
                splitPos = indexOfDoubleQuote;

                // The character to surround the problem quote with
                outerQuoteToUse = SINGLE_QUOTE;
            } else {
                // No problem quote found
                splitPos = -1;

                // Still need to add the part to the safe string
                outerQuoteToUse = SINGLE_QUOTE;
            }

            if (splitPos >= 0) {
                // The part which does not have a single quote or double quote
                String beforePart = StringUtils.substring(afterPart, 0, splitPos);

                // Update the safe string with the before part and the problem quote
                safe += outerQuoteToUse + beforePart + outerQuoteToUse + SEPARATOR
                        + outerQuoteToUse + problemQuote + outerQuoteToUse + SEPARATOR;

                // Update the after part which still needs to be processed
                afterPart = StringUtils.substring(afterPart, splitPos + 1);
            } else {
                // Add the last part to the safe string
                safe += outerQuoteToUse + afterPart + outerQuoteToUse + SEPARATOR;
            }
        } while (splitPos >= 0);

        safe = StringUtils.removeEnd(safe, ",") + ")";
        return safe;
    }

    /**
     * Find 1st problem quote (if any)
     *
     * @param indexOfSingleQuote - Index of (1st) Single Quote (negative indicates no single quote)
     * @param indexOfDoubleQuote - Index of (1st) Double Quote (negative indicates no double quote)
     * @return null if no problem quote else the 1st problem quote (SINGLE_QUOTE or DOUBLE_QUOTE)
     */
    private static String findFirstProblemQuote(int indexOfSingleQuote, int indexOfDoubleQuote) {
        if (indexOfSingleQuote >= 0 && indexOfDoubleQuote >= 0) {
            if (indexOfSingleQuote < indexOfDoubleQuote) {
                return SINGLE_QUOTE;
            } else {
                return DOUBLE_QUOTE;
            }
        }

        if (indexOfSingleQuote >= 0) {
            return SINGLE_QUOTE;
        }

        if (indexOfDoubleQuote >= 0) {
            return DOUBLE_QUOTE;
        }

        return null;
    }

    /**
     * Delay to use bypass issues with JavaScript processing.<BR>
     * <B>Notes: </B> This method should only used when WebDriver methods are not successful.<BR>
     * <B>Example Usages:</B><BR>
     * <OL>
     * <LI>
     * Javascript to process inline validation errors.
     * </LI>
     * </OL>
     */
    public static void delayForJavaScriptProcessing() {
        sleep(500);
    }

    /**
     * Check if the element's class attribute contains the specified style value
     *
     * @param element              - Element to get the class attribute
     * @param style                - The style to match in the class attribute
     * @param removeTrailingDigits - true to remove trailing digits from class types before comparison
     * @return true if matching style is found in the class attribute of the element
     */
    public static boolean containsStyle(WebElement element, String style, boolean removeTrailingDigits) {
        // Split the class attribute into all the 'class types' such that we can find similar 'class types'.
        // For example, we can find 'top' only instead of matching 'ad-leader-top' in
        // class="banner leaderboard top ad-leader-top breakpoint-750-hide"
        String[] classes = Utils.splitData(StringUtils.defaultString(element.getAttribute("class")), " ");

        for (String classType : classes) {
            // By removing the trailing digits which are normally random values, we can match on a "base" style
            // Example, the class type of "banner-548939736" would match "banner-" such that user does not
            // need to know the random number that is appended.
            String classTypeForCompare = (removeTrailingDigits) ? classType.trim().replaceAll("\\d*$", "") : classType.trim();
            if (StringUtils.equalsIgnoreCase(classTypeForCompare, style)) {
                return true;
            }
        }

        return false;
    }

}
