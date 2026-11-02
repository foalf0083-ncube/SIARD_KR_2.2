// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.api;

public interface Cell extends Value {
  Record getParentRecord();
  
  MetaColumn getMetaColumn();
}
