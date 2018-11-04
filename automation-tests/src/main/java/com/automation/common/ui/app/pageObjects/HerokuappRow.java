package com.automation.common.ui.app.pageObjects;

import com.taf.automation.ui.support.PageObjectV2;
import com.taf.automation.ui.support.TestContext;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import org.openqa.selenium.support.FindBy;
import ui.auto.core.components.WebComponent;

import java.util.HashMap;
import java.util.Map;

/**
 * Example of using dynamic locators to work with a row in a table.<BR>
 * Site:  <a href="https://the-internet.herokuapp.com/tables">https://the-internet.herokuapp.com/tables</a><BR>
 * Table from Example 2
 */
public class HerokuappRow extends PageObjectV2 {
    private Map<String, String> substitutions;

    @XStreamOmitField
    @FindBy(css = "[id='${row-id}'] .last-name")
    private WebComponent lastName;

    @XStreamOmitField
    @FindBy(css = "[id='${row-id}'] .first-name")
    private WebComponent firstName;

    @XStreamOmitField
    @FindBy(css = "[id='${row-id}'] .email")
    private WebComponent email;

    @XStreamOmitField
    @FindBy(css = "[id='${row-id}'] .dues")
    private WebComponent dues;

    @XStreamOmitField
    @FindBy(css = "[id='${row-id}'] .web-site")
    private WebComponent website;

    public HerokuappRow() {
        super();
    }

    private Map<String, String> getSubstitutions() {
        if (substitutions == null) {
            substitutions = new HashMap<>();
        }

        return substitutions;
    }

    public void updateRowIdKey(String value) {
        getSubstitutions().put("row-id", value);
    }

    public void initPage(TestContext context) {
        initPage(context, getSubstitutions());
    }

    public String getLastName() {
        return lastName.getText();
    }

    public String getFirstName() {
        return firstName.getText();
    }

    public String getEmail() {
        return email.getText();
    }

    public String getDues() {
        return dues.getText();
    }

    public String getWebsite() {
        return website.getText();
    }

}
