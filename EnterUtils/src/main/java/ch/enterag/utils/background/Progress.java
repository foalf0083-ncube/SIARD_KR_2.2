// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.enterag.utils.background;

public interface Progress {
  boolean cancelRequested();
  
  void notifyProgress(int paramInt);
}
