package com.craftinginterpreters.lox;

import java.util.List;
import java.util.ArrayList;

import static com.craftinginterpreters.lox.TokenType.*;

class Parser {
  private static class ParseError extends RuntimeException {}

  private final List<Token> tokens;
  private int current = 0;

  Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  List<Stmt> parse() {
    List<Stmt> statements = new ArrayList<>();

    while (!isAtEnd()) {
      // entrypoint: statement() rule
      statements.add(declaration());
    }

    return statements;
  }

  private Stmt declaration() {
    try {
      if (match(VAR)) return varDeclaration();

      return statement();
    } catch (ParseError error) {
      synchronize();
      return null;
    }
  }

  private Stmt varDeclaration() {
    Token name = consume(IDENTIFIER, "Expect variable name.");

    Expr initializer = null; // default init to null (nil)
    if (match(EQUAL)) {
      initializer = expression(); // variable or value
    }

    consume(SEMICOLON, "Expect ';' after variable declaration.");
    return new Stmt.Var(name, initializer);
  }

  private Stmt statement() {
    if (match(PRINT)) return printStatement();
    // assume non-print statements are expressions statements
    return expressionStatement();
  }

  private Expr expression() {
    return assignment();
  }

  private Expr assignment() {
    // parse expression as per normal
    // includes matching IDENTIFIER (variable)
    // e.g. newPoint(x + 2, 0).y = 3;
    Expr expr = equality();

    if (match(EQUAL)) {
      Token equals = previous();
      // recursively call assignment() since it is
      // right-associative (i.e. right-most parsed first)
      Expr value = assignment();

      // create new assignment expression node iff
      // left-side of EQUAL is a variable (l-value)
      if (expr instanceof Expr.Variable) {
        Token name = ((Expr.Variable)expr).name;
        return new Expr.Assign(name, value);
      }

      // else report but don’t throw error here
      // parser isn’t in a confused state where we need to go into panic mode and synchronize
      error(equals, "Invalid assignment target.");
    }

    return expr;
  }

  private Stmt printStatement() {
    Expr value = expression();
    consume(SEMICOLON, "Expect ';' after value.");
    return new Stmt.Print(value);
  }

  private Stmt expressionStatement() {
    Expr expr = expression();
    consume(SEMICOLON, "Expect ';' after expression.");
    return new Stmt.Expression(expr);
  }

  private Expr equality() {
    Expr expr = comparison();

    while (match(BANG_EQUAL, EQUAL_EQUAL)) {
      Token operator = previous();
      Expr right = comparison();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr comparison() {
    Expr expr = term();

    while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
      Token operator = previous();
      Expr right = term();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr term() {
    Expr expr = factor();

    while (match(MINUS, PLUS)) {
      Token operator = previous();
      Expr right = factor();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr factor() {
    Expr expr = unary();

    while (match(SLASH, STAR)) {
      Token operator = previous();
      Expr right = unary();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr unary() {
    if (match(BANG, MINUS)) {
      // get previous token, since match advances pointer
      Token operator = previous();
      // recursively try to match more unary operators
      Expr right = unary();
      return new Expr.Unary(operator, right);
    }

    return primary();
  }

  private Expr primary() {
    if (match(FALSE)) return new Expr.Literal(false);
    if (match(TRUE)) return new Expr.Literal(true);
    if (match(NIL)) return new Expr.Literal(null);

    if (match(NUMBER, STRING)) {
      // numbers/strings have 2 tokens added by Scanner.java
      return new Expr.Literal(previous().literal);
    }

    if (match(IDENTIFIER)) {
      return new Expr.Variable(previous());
    }

    if (match(LEFT_PAREN)) {
      Expr expr = expression();
      consume(RIGHT_PAREN, "Expect ')' after expression.");
      return new Expr.Grouping(expr);
    }

    // token is not a valid expression
    throw error(peek(), "Expect expression.");
  }

  private boolean match(TokenType... types) {
    for (TokenType type : types) {
      if (check(type)) {
        advance();
        return true;
      }
    }

    return false;
  }

  private Token consume(TokenType type, String message) {
    if (check(type)) return advance();

    throw error(peek(), message);
  }

  private boolean check(TokenType type) {
    if (isAtEnd()) return false;
    return peek().type == type;
  }

  private Token advance() {
    if (!isAtEnd()) current++;
    return previous();
  }

  private boolean isAtEnd() {
    return peek().type == EOF;
  }

  private Token peek() {
    return tokens.get(current);
  }

  private Token previous() {
    return tokens.get(current - 1);
  }

  /* Returns the error instead of throwing it.
     - let calling method (in parser) decide to unwind or not
     - some parse errors occur in places where the parser isn’t
     likely to get into a weird state (no need to synchronize)
     - in those places, we simply report the error and continue

     Example: Lox limits no. of args passed to a function
     - pass too many: parser needs to report that error
     - but it can and should simply keep on parsing the extra
     arguments, instead of freaking out and going into panic mode
  */
  private ParseError error(Token token, String message) {
    Lox.error(token, message);
    return new ParseError();
  }

  /* best-effort synchronization
     - discard tokens until the beginning of next statement
     - boundary picked: semicolon
     - not perfect since semicolon could be in a for-loop
  */
  private void synchronize() {
    advance();

    while (!isAtEnd()) {
      // discard the semicolon token
      if (previous().type == SEMICOLON) return;

      // known-good statement boundaries to resume parsing
      switch (peek().type) {
        case CLASS:
        case FUN:
        case VAR:
        case FOR:
        case IF:
        case WHILE:
        case PRINT:
        case RETURN:
          return;
      }

      advance();
    }
  }
}
