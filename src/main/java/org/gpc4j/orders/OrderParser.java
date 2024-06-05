package org.gpc4j.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.gpc4j.orders.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class OrderParser {

  /**
   * DSL Rules defining how to associate columns in the CSVRecord with
   * fields/methods in the Order class.
   */
  private JsonNode transformRules;

  Logger LOG = LoggerFactory.getLogger(OrderParser.class);


  /**
   * Create OrderParser using the transform rules provided.
   *
   * @param rulesFileName Containing JSON Rules
   */
  public OrderParser(String rulesFileName) throws IOException {
    this(new File(rulesFileName));
  }

  /**
   * Create OrderParser using the transform rules provided.
   *
   * @param rulesFile Containing JSON Rules
   */
  public OrderParser(File rulesFile) throws IOException {
    this(new FileInputStream(rulesFile));
  }

  /**
   * Create OrderParser using the transform rules provided.
   *
   * @param iStream Containing JSON Rules
   */
  public OrderParser(InputStream iStream) throws IOException {
    this(new InputStreamReader(iStream));
  }

  /**
   * Create OrderParser using the transform rules provided.
   *
   * @param reader Containing JSON Rules
   */
  public OrderParser(final Reader reader) throws IOException {
    this.transformRules = new ObjectMapper().readTree(reader);
  }


  /**
   * Parse the CSV file name provided and return a Stream of Orders.
   *
   * @throws IOException
   */
  public Stream<Order> parse(final String csvFileName) throws IOException {
    return parse(new File(csvFileName));
  }

  /**
   * Parse the CSV File provided and return a Stream of Orders.
   *
   * @throws IOException
   */
  public Stream<Order> parse(final File csvFile) throws IOException {
    return parse(new FileInputStream(csvFile));
  }

  /**
   * Parse the CSV InputStream provided and return a Stream of Orders.
   *
   * @throws IOException
   */
  public Stream<Order> parse(final InputStream iStream) throws IOException {
    return parse(new InputStreamReader(iStream));
  }

  /**
   * Parse the CSV Reader provided and return a Stream of Orders.
   *
   * @throws IOException
   */
  public Stream<Order> parse(final Reader reader) throws IOException {
    CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT);
    return parser.getRecords()
        .parallelStream()
        .map(process);
  }


  /**
   * Parses the CSVRecord provided and returns an Order based on
   * entries in the record.
   * <p>
   * Visibility is package private for easier Unit Testing.
   */
  final Function<CSVRecord, Order> process = csvRecord -> {

    final Order order = new Order();

    ArrayNode transforms = (ArrayNode) transformRules.get("transforms");

    try {
      for (JsonNode transform : transforms) {

        // The name of the setter method to call on the Order class.
        final String methodName = transform.get("method").textValue();

        // The Class definition section of transform rule.
        final JsonNode classDef = transform.get("class");

        // The name of the Class that is used as a value in the
        // methodName defined above.
        final String className = classDef.get("name").textValue();

        final Class<?> clazz =
            getClass().getClassLoader().loadClass(className);

        // Get the method to call as defined in the transform
        final Method method = order.getClass().getMethod(methodName, clazz);

        if (className.equals(Date.class.getName())) {
          // Special case for Dates.
          final String dateFormat = classDef.get("dateFormat").textValue();
          DateFormat formatter = new SimpleDateFormat(dateFormat);
          Date date = formatter.parse(reFormat(classDef, csvRecord));
          method.invoke(order, date);
        } else {
          /*
           Find String constructor for given class and create an instance
           of it using the template that has been formatted with
           corresponding value(s) from the CSV Entry
           */
          Object value = clazz
              .getDeclaredConstructor(String.class)
              .newInstance(reFormat(classDef, csvRecord));
          method.invoke(order, value);
        }

      }

    } catch (NoSuchMethodException ex) {
      LOG.error("Error with Transform Rules file: " + ex.toString());
      throw new IllegalArgumentException(ex);
    } catch (ReflectiveOperationException ex) {
      LOG.error(ex.toString());
      LOG.error("Error with " + csvRecord + "; " + ex.getCause());
    } catch (ParseException | ArrayIndexOutOfBoundsException ex) {
      LOG.error("Error with " + csvRecord + "; " + ex);
    }

    return order;
  };


  /**
   * Replace template with column values and filter out strings if defined in
   * transform.
   */
  String reFormat(final JsonNode classDef, final CSVRecord record) {

    final String template = classDef.get("template").textValue();
    final ArrayNode filters = (ArrayNode) classDef.get("filters");

    final Pattern p = Pattern.compile("(\\{\\d})");
    final Matcher m = p.matcher(template);
    String result = template;

    // Find the columns in the template and substitute them
    // with record values.
    while (m.find()) {
      final String group = m.group();
      final String column = group
          .replace("{", "")
          .replace("}", "");
      result = result.replace(group, record.get(Integer.parseInt(column)));
    }

    // Filter out any Strings as defined in the transform.
    if (filters != null) {
      for (JsonNode filter : filters) {
        result = result.replaceAll(filter.textValue(), "");
      }
    }

    JsonNode caseNode = classDef.get("case");
    if (null != caseNode) {
      if ("proper".equalsIgnoreCase(caseNode.textValue().trim())) {
        // Modify case
        result = result.substring(0, 1).toUpperCase() +
            result.substring(1).toLowerCase();
      }
    }

    return result;
  }
}
