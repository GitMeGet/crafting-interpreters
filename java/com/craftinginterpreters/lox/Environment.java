package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

class Environment {
  final Environment enclosing; // pointer to outer env
  private final Map<String, Object> values = new HashMap<>();

  // root env (no outer)
  Environment() {
    enclosing = null;
  }

  // called when a new {} block encountered in Interpreter.java
  Environment(Environment enclosing) {
    this.enclosing = enclosing;
  }

  Object get(Token name) {
    if (values.containsKey(name.lexeme)) {
      return values.get(name.lexeme);
    }

    // recursively walk up chain to find variable
    if (enclosing != null) return enclosing.get(name);

    /* why run-time error?
       - hard to check for syntax error at compile-time (single-pass parsing of code)
       - if a variable is evaluated (used), only variables that have already been parsed are valid
       - can refer to a variable before it’s defined as long as you don’t evaluate the reference (e.g. mutually recursive methods)
    */
    throw new RuntimeError(name,
        "Undefined variable '" + name.lexeme + "'.");
  }

  void assign(Token name, Object value) {
    // assignment is not allowed to create a new variable
    if (values.containsKey(name.lexeme)) {
      values.put(name.lexeme, value);
      return;
    }

    // recursively check outer env
    if (enclosing != null) {
      enclosing.assign(name, value);
      return;
    }

    throw new RuntimeError(name,
        "Undefined variable '" + name.lexeme + "'.");
  }

  /* note: can be used to redefine an existing variable
       > var a = "before";
       > print a; // "before".
       > var a = "after";
       > print a; // "after".
  */
  void define(String name, Object value) {
    values.put(name, value);
  }

}
