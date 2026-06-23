package recieptservice.com.recieptservice;

interface PrinterInterface {
    void printText(String text);
    void setAlignment(int alignment);
    void setTextSize(float textSize);
    void nextLine(int line);
    void setTextBold(boolean bold);
    void setDark(int value);
    void setLineHeight(float lineHeight);
    void setTextDoubleWidth(boolean enable);
    void setTextDoubleHeight(boolean enable);
    void beginWork();
    void endWork();
    String getServiceVersion();
}
