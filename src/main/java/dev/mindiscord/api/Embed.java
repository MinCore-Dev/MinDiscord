package dev.mindiscord.api;

import java.util.List;

public final class Embed {
  public String title;
  public String description;
  public String url;
  public Integer color;
  public Author author;
  public Footer footer;
  public Thumbnail thumbnail;
  public Image image;
  public List<Field> fields;

  public static final class Author { public String name, url, iconUrl; }
  public static final class Footer { public String text, iconUrl; }
  public static final class Thumbnail { public String url; }
  public static final class Image { public String url; }
  public static final class Field { public String name, value; public boolean inline; }
}
