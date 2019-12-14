
#### Overview

The OrderParser class is the main deliverable in this project.

It has various convenience constructors for constructing an instance 
using a JSON based Rules file (defined below) that is needed for 
determining the necessary transformations from CSV to Order.  

The intent is that the client/user will call one of the parse 
methods of the OrderParser class which will read the CSV content 
return a Stream<Order> of Orders.  CSV entries are processed using 
a parallelStream() to take advantage of multi-threading.

The OrderParser class does not know about the various fields/methods 
of the Order class but uses Reflection based on the transform values 
in the JSON Rules file to set the values of various parameters.  

#### Rules File Definitions:

There is a sample in src/test/resources/dsl.json which is based 
on the requirements contained in the PDF requirements document.

In the "transforms" array there are entries such as this:

    {
      "method": "setOrderId",
      "class": {
        "name": "java.lang.Integer",
        "template": "{0}"
      }
    }

In this example the "method" is the method on the Order class 
that is to be called to set the value.  The "class" is the 
name of the class used to set the value in the method.  

The "template" is the string used to create the class which 
has a {n} value where n is the column of the CSV string that 
the Order is being created from. The "template" value can 
contain one or more column values.  

Below is a special case for Dates where the date format and 
how to construct the template from multiple CSV columns:

      "method": "setOrderDate",
      "class": {
        "name": "java.util.Date",
        "dateFormat": "yyyy-MM-dd",
        "template": "{1}-{2}-{3}"
      }
    }

In this example the template string is create from CSV 
columns 1, 2, and 3 joined together with dashes to 
match the "dateFormat".

There is also a "filter" mechanism which is used below to 
filter out any particular string from the resulting string 
after the template has been filled in.  Here, the "," string 
is removed prior to creating a BigDecimal with the resulting 
string from the template.

    {
      "method": "setQuantity",
      "class": {
        "name": "java.math.BigDecimal",
        "template": "{6}",
        "filters": [
          ","
        ]
      }

The "proper case" functionality is achieved by setting "case" 
as part of the "class" definition for Strings 

      "method": "setProductName",
      "class": {
        "name": "java.lang.String",
        "case": "proper",
        "template": "{5}"
      }
    }

#### Usage

    // Create OrderParser using Rules file
    OrderParser orderParser = new OrderParser("src/test/resources/dsl.json");
    // Parse CSV file to a List of Orders
    List<Order> orders = orderParser.parse("src/test/resources/data1.csv")
        .collect(Collectors.toList());

#### Future Steps

The OrderParser class has a few "magic strings" in it which should 
be pulled up to static final field constants.

Additional Test cases could be created as well.

