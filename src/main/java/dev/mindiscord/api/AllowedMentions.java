package dev.mindiscord.api;

import java.util.List;

public final class AllowedMentions {
  public boolean parseRoles;
  public boolean parseUsers;
  public boolean parseEveryone;
  public List<String> roles;
  public List<String> users;
}
