// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.enterag.utils.xml;

import ch.enterag.utils.BU;
import ch.enterag.utils.SU;
import java.text.StringCharacterIterator;
import org.w3c.dom.Element;
import org.w3c.dom.Node;











public class XU
{
  public static final String sXML_VERSION_1_0 = "1.0";
  private static final int m_iCODES = 256;
  private static String[] m_asToDom = null;





  
  private static void buildToDom() {
    m_asToDom = new String[m_iCODES];
    int i;
    for (i = 0; i < m_iCODES; i++) {
      m_asToDom[i] = String.valueOf((char)i);
    }
    for (i = 0; i < 32; i++) {
      m_asToDom[i] = "\\u00" + BU.toHex((byte)i);
    }
    m_asToDom[92] = "\\u005C";
    
    for (i = 127; i < 160; i++) {
      
      byte b = (byte)(0xFFFFFF00 | i);
      if (i < 128)
        b = (byte)i; 
      m_asToDom[i] = "\\u00" + BU.toHex(b);
    } 
  }











  
  public static String toXml(String sText) {
    if (m_asToDom == null)
      buildToDom(); 
    if (SU.isNotEmpty(sText)) {
      
      StringBuffer sb = new StringBuffer();
      String[] asLookup = null;
      asLookup = m_asToDom;
      StringCharacterIterator sci = new StringCharacterIterator(sText);
      boolean bSpace = true; char c;
      for (c = sci.first(); c != Character.MAX_VALUE; c = sci.next()) {
        
        if (bSpace && c == ' ') {
          sb.append("\\u0020");
        } else {
          
          if (c < m_iCODES) {
            sb.append(asLookup[c]);
          } else {
            sb.append(c);
          }  bSpace = (c == ' ');
        } 
      } 
      
      if (sb.charAt(sb.length() - 1) == ' ') {
        
        sb.setLength(sb.length() - 1);
        sb.append("\\u0020");
      } 
      sText = sb.toString();
    } 
    return sText;
  }








  
  public static void toXml(String sText, Element el) {
    if (sText != null) {
      el.appendChild(el.getOwnerDocument().createTextNode(toXml(sText)));
    }
  }







  
  /*
   * 메타데이터의 형식·스키마 이름처럼 같은 짧은 문자열이 자료를 쓰는 동안 값 하나마다 되풀이해 복원된다.
   * 공간 자료형의 좌표 배열은 원소마다 이 경로를 타서 추출 시간의 절반 이상을 여기서 썼다.
   * 복원은 입력만으로 정해지므로 짧은 문자열의 결과를 기억해 둔다.
   */
  private static final int FROM_XML_CACHE_MAX_LENGTH = 256;
  private static final int FROM_XML_CACHE_MAX_ENTRIES = 4096;
  private static final java.util.concurrent.ConcurrentHashMap<String, String> _mapFromXml =
      new java.util.concurrent.ConcurrentHashMap<String, String>();

  public static String fromXml(String sText) {
    if (sText == null || sText.length() > FROM_XML_CACHE_MAX_LENGTH)
      return fromXmlUncached(sText);
    String sResult = _mapFromXml.get(sText);
    if (sResult == null) {
      sResult = fromXmlUncached(sText);
      if (sResult != null) {
        if (_mapFromXml.size() >= FROM_XML_CACHE_MAX_ENTRIES)
          _mapFromXml.clear();
        _mapFromXml.put(sText, sResult);
      }
    }
    return sResult;
  }

  private static String fromXmlUncached(String sText) {
    if (SU.isNotEmpty(sText)) {
      
      StringBuffer sb = new StringBuffer();
      StringCharacterIterator sci = new StringCharacterIterator(sText);
      boolean bSpace = false; char c;
      for (c = sci.first(); c != Character.MAX_VALUE; ) {
        
        if (Character.isWhitespace(c))
          c = ' '; 
        boolean bIncrement = true;
        if (!bSpace || c != ' ')
        {

          
          if (c == '\\') {
            
            c = sci.next();
            if (c == 'u') {
              
              char[] acHex4 = new char[4];
              
              for (int i = 0; i < 4; i++) {
                
                if (c != Character.MAX_VALUE) {
                  
                  c = sci.next();
                  if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f')) {
                    acHex4[i] = c;
                  } else {
                    acHex4[i] = '0';
                  } 
                } else {
                  acHex4[i] = '0';
                } 
              }  sb.append((char)BU.fromHex(acHex4[0], acHex4[1], acHex4[2], acHex4[3]));
            }
            else {
              
              sb.append('\\');
              bIncrement = false;
            } 
          } else {
            
            sb.append(c);
          }  }  if (bIncrement) {
          
          bSpace = (c == ' ');
          c = sci.next();
        } 
      } 
      sText = sb.toString();
    } 
    return sText;
  }








  
  public static String fromXml(Element el) {
    String sText = null;
    if (el != null)
      sText = el.getTextContent(); 
    return fromXml(sText);
  }






  
  public static void clearElement(Element el) {
    for (int iChild = el.getChildNodes().getLength() - 1; iChild >= 0; iChild--) {
      
      Node node = el.getChildNodes().item(iChild);
      el.removeChild(node);
    } 
  }
}
