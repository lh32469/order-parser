package org.gpc4j.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.gpc4j.orders.model.Order;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

public class OrderParserTest {

  OrderParser orderParser;

  @Rule
  public TestName testName = new TestName();

  Logger LOG = LoggerFactory.getLogger(OrderParserTest.class);


  @Before
  public void setUp() throws Exception {
    LOG.info("++++++  " + testName.getMethodName() + "  ++++++");

    InputStream iStream =
        getClass().getClassLoader().getResourceAsStream("dsl.json");

    // Default OrderParser if not created locally
    orderParser = new OrderParser(iStream);
  }


  @After
  public void tearDown() {
    LOG.info("------  " + testName.getMethodName() + "  ------");
  }

  /**
   * Test ability to parse an InputStream of CSV Data and InputStream
   * of Rules file.
   */
  @Test
  public void inputStream() throws IOException {

    InputStream iStreamRules =
        getClass().getClassLoader().getResourceAsStream("dsl.json");
    orderParser = new OrderParser(iStreamRules);

    InputStream iStream = getClass()
        .getClassLoader()
        .getResourceAsStream("data1.csv");

    List<Order> orders = orderParser.parse(iStream)
        .peek(order -> LOG.trace("order = " + order))
        .collect(Collectors.toList());

    validateOrders(orders);
  }

  /**
   * Test ability to parse a Reader of CSV Data
   */
  @Test
  public void reader() throws IOException {
    InputStream iStream = getClass()
        .getClassLoader()
        .getResourceAsStream("data1.csv");

    List<Order> orders = orderParser.parse(new InputStreamReader(iStream))
        .peek(order -> LOG.trace("order = " + order))
        .collect(Collectors.toList());

    validateOrders(orders);
  }

  /**
   * Test that invalid OrderId logs appropriate NumberFormatException
   * and CVSRecord.
   */
  @Test
  public void badOrderId() throws IOException {
    final String csvString = "one Hundred,2018,1,1,P-10001,Arugola,\"5,250.50\",Lorem,Ipsum,\n";
    CSVParser parser = new CSVParser(new StringReader(csvString), CSVFormat.DEFAULT);
    CSVRecord record = parser.getRecords().get(0);
    orderParser.LOG = mock(Logger.class);

    orderParser.process.apply(record);
    verify(orderParser.LOG).error(contains("Error with CSVRecord"));
    verify(orderParser.LOG).error(contains("NumberFormatException: For input string: \"one Hundred\""));
  }

  /**
   * Test that invalid Year logs appropriate ParseException
   * and CVSRecord.
   */
  @Test
  public void badYear() throws IOException {
    final String csvString = "1001,Year,1,1,P-10001,Arugola,\"5,250.50\",Lorem,Ipsum,\n";
    CSVParser parser = new CSVParser(new StringReader(csvString), CSVFormat.DEFAULT);
    CSVRecord record = parser.getRecords().get(0);
    orderParser.LOG = mock(Logger.class);

    orderParser.process.apply(record);
    verify(orderParser.LOG).error(contains("Error with CSVRecord"));
    verify(orderParser.LOG).error(contains("ParseException: Unparseable date: \"Year-1-1\""));
  }

  /**
   * Test that invalid Month logs appropriate ParseException
   * and CVSRecord.
   */
  @Test
  public void badMonth() throws IOException {
    final String csvString = "1001,2019,Jan,1,P-10001,Arugola,\"5,250.50\",Lorem,Ipsum,\n";
    CSVParser parser = new CSVParser(new StringReader(csvString), CSVFormat.DEFAULT);
    CSVRecord record = parser.getRecords().get(0);
    orderParser.LOG = mock(Logger.class);

    orderParser.process.apply(record);
    verify(orderParser.LOG).error(contains("Error with CSVRecord"));
    verify(orderParser.LOG).error(contains("ParseException: Unparseable date: \"2019-Jan-1\""));
  }

  /**
   * Test that invalid Day logs appropriate ParseException
   * and CVSRecord.
   */
  @Test
  public void badDay() throws IOException {
    final String csvString = "1001,2019,1,Tue,P-10001,Arugola,\"5,250.50\",Lorem,Ipsum,\n";
    CSVParser parser = new CSVParser(new StringReader(csvString), CSVFormat.DEFAULT);
    CSVRecord record = parser.getRecords().get(0);
    orderParser.LOG = mock(Logger.class);

    orderParser.process.apply(record);
    verify(orderParser.LOG).error(contains("Error with CSVRecord"));
    verify(orderParser.LOG).error(contains("ParseException: Unparseable date: \"2019-1-Tue\""));
  }

  /**
   * Test that invalid Quantity logs appropriate NumberFormatException
   * and CVSRecord.
   */
  @Test
  public void badQuantity() throws IOException {
    final String csvString = "1001,2019,1,1,P-10001,Arugola,\"5.250.50\",Lorem,Ipsum,\n";
    CSVParser parser = new CSVParser(new StringReader(csvString), CSVFormat.DEFAULT);
    CSVRecord record = parser.getRecords().get(0);
    orderParser.LOG = mock(Logger.class);

    orderParser.process.apply(record);
    verify(orderParser.LOG).error(contains("Error with CSVRecord"));
    verify(orderParser.LOG).error(contains("NumberFormatException: Character array contains more than one decimal point."));
  }

  /**
   * Test that invalid Quantity logs appropriate ArrayIndexOutOfBoundsException
   * and CVSRecord.
   */
  @Test
  public void wrongNumberOfColumns() throws IOException {
    final String csvString = "1001,2019,1,1,P-10001,Arugola\n";
    CSVParser parser = new CSVParser(new StringReader(csvString), CSVFormat.DEFAULT);
    CSVRecord record = parser.getRecords().get(0);
    orderParser.LOG = mock(Logger.class);

    orderParser.process.apply(record);
    verify(orderParser.LOG).error(contains("Error with CSVRecord"));
    verify(orderParser.LOG).error(contains("out of bounds for length"));
    verify(orderParser.LOG).error(contains("ArrayIndexOutOfBoundsException: "));
  }


  /**
   * Test that Transform Rules file containing wrong method name logs error
   * and throws IllegalArgumentException.
   */
  @Test
  public void wrongMethodName() throws IOException {
    final String csvString = "1001,2019,1,1,P-10001,Arugola,\"5,250.50\",Lorem,Ipsum,\n";
    CSVParser parser = new CSVParser(new StringReader(csvString), CSVFormat.DEFAULT);
    CSVRecord record = parser.getRecords().get(0);

    ObjectMapper mapper = new ObjectMapper();
    JsonNode rules = mapper.readTree(
        "{\n" +
            "  \"transforms\": [\n" +
            "    {\n" +
            "      \"method\": \"bogusMethod\",\n" +
            "      \"class\": {\n" +
            "        \"name\": \"java.lang.Integer\",\n" +
            "        \"template\": \"{0}\"\n" +
            "      }\n" +
            "    }\n" +
            "  ]\n" +
            "}");

    orderParser = new OrderParser(new StringReader(rules.toString()));
    orderParser.LOG = mock(Logger.class);

    try {
      orderParser.process.apply(record);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException ex) {
    }

    verify(orderParser.LOG).error(contains("NoSuchMethodException"));
    verify(orderParser.LOG).error(contains("Error with Transform Rules file"));
    verify(orderParser.LOG).error(contains("bogusMethod"));
  }

  /**
   * Test that Transform Rules file containing wrong method class for method logs error
   * and throws IllegalArgumentException.
   */
  @Test
  public void wrongMethodSignature() throws IOException {
    final String csvString = "1001,2019,1,1,P-10001,Arugola,\"5,250.50\",Lorem,Ipsum,\n";
    CSVParser parser = new CSVParser(new StringReader(csvString), CSVFormat.DEFAULT);
    CSVRecord record = parser.getRecords().get(0);

    ObjectMapper mapper = new ObjectMapper();
    JsonNode rules = mapper.readTree(
        "{\n" +
            "  \"transforms\": [\n" +
            "    {\n" +
            "      \"method\": \"setOrderId\",\n" +
            "      \"class\": {\n" +
            "        \"name\": \"java.lang.String\",\n" +
            "        \"template\": \"{0}\"\n" +
            "      }\n" +
            "    }\n" +
            "  ]\n" +
            "}");

    orderParser = new OrderParser(new StringReader(rules.toString()));
    orderParser.LOG = mock(Logger.class);

    try {
      orderParser.process.apply(record);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException ex) {
    }

    verify(orderParser.LOG).error(contains("NoSuchMethodException"));
    verify(orderParser.LOG).error(contains("Error with Transform Rules file"));
    verify(orderParser.LOG).error(contains("setOrderId(java.lang.String)"));
  }


  /**
   * Test ability to parse a file of CSV Data using fileName of CSV file
   * and fileName of Rules file.
   */
  @Test
  public void fileName() throws IOException {
    OrderParser orderParser = new OrderParser("src/test/resources/dsl.json");
    List<Order> orders = orderParser.parse("src/test/resources/data1.csv")
        .peek(order -> LOG.trace("order = " + order))
        .collect(Collectors.toList());

    validateOrders(orders);
  }

  /**
   * Test that ProductNames are properly cased where the first character
   * is capitalized and all the remaining are lower case.
   */
  @Test
  public void properCase() throws IOException {
    final String csvString = "1001,2019,1,1,P-10001,ARUGoLA,\"5,250.50\",Lorem,Ipsum,\n";
    CSVParser parser = new CSVParser(new StringReader(csvString), CSVFormat.DEFAULT);
    CSVRecord record = parser.getRecords().get(0);
    Order o = orderParser.process.apply(record);
    assertEquals("Arugola", o.getProductName());
  }

  /**
   * Helper method that validates known Orders are parsed correctly.
   */
  void validateOrders(List<Order> orders) {
    // No guarantee in order.
    Order order0 = orders.stream()
        .filter(order -> order.getOrderId() == 1000)
        .findFirst()
        .get();
    Order order1 = orders.stream()
        .filter(order -> order.getOrderId() == 1001)
        .findFirst()
        .get();

    assertEquals(new BigDecimal("5250.50"),
        order0.getQuantity());
    assertEquals(new BigDecimal("500.00"),
        order1.getQuantity());
  }


}