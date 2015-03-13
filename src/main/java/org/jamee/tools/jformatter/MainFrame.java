package org.jamee.tools.jformatter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Element;
import javax.swing.text.Highlighter;
import javax.xml.parsers.DocumentBuilderFactory;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.ObjectMapper;
import org.w3c.dom.Document;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.esotericsoftware.yamlbeans.YamlWriter;

class FormartException extends Exception {
    private static final long serialVersionUID = -1039143799189264130L;
    private final int lineNumber;
    private int column;

    public FormartException(Throwable cause, int lineNumber, int column) {
        super(cause);
        this.lineNumber = lineNumber;
        this.column = column;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public int getColumn() {
        return column;
    }
}

interface Formatter {
    String format(String text) throws FormartException;
}

class JSONFormatter implements Formatter {
    public String format(String text) throws FormartException {
        ObjectMapper mapper = new ObjectMapper();
        try {
            Object json = mapper.readValue(text, Object.class);
            return mapper.defaultPrettyPrintingWriter().writeValueAsString(json);
        } catch (JsonParseException e) {
            throw new FormartException(e, e.getLocation().getLineNr() - 1, e.getLocation().getColumnNr() -1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}


class XMLFormatter implements Formatter {
    public String format(String text) throws FormartException {
           try {
                final InputSource src = new InputSource(new StringReader(text));
                Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(src);
                //final Node node = document.getDocumentElement();
                    //May need this: System.setProperty(DOMImplementationRegistry.PROPERTY,"com.sun.org.apache.xerces.internal.dom.DOMImplementationSourceImpl");
                final DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
                final DOMImplementationLS impl = (DOMImplementationLS) registry.getDOMImplementation("LS");
                final LSSerializer writer = impl.createLSSerializer();

                String encoding = "utf-8";
                if (null != document.getXmlEncoding()) {
                    encoding = document.getXmlEncoding();
                }
                System.out.println("encoding: " +  document.getXmlEncoding());
                writer.getDomConfig().setParameter("format-pretty-print", Boolean.TRUE); // Set this to true if the output needs to be beautified.
                //writer.getDomConfig().setParameter("xml-declaration", true); // Set this to true if the declaration is needed to be outputted.
                //writer.getDomConfig().setParameter(OutputKeys.ENCODING, encoding);
                return writer.writeToString(document);
            } catch (org.xml.sax.SAXParseException e) {
                throw new FormartException(e, e.getLineNumber() - 1, e.getColumnNumber() - 1);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e); // simple exception handling, please review it
            }
    }
}


class YAMLFormatter implements Formatter {
    public String format(String text) {
        try {
            YamlReader reader = new YamlReader(new StringReader(text));
            Object obj = reader.read();
            StringWriter sw = new StringWriter();
            YamlWriter writer = new YamlWriter(sw);
            writer.write(obj);
            writer.close();
            return sw.toString();
        } catch (YamlException e) {
            throw new RuntimeException(e);
        }
    }
}

public class MainFrame extends JFrame {
    private static final long serialVersionUID = 8117459946823901839L;

    private static Map<String, Formatter> formatters = new HashMap<String, Formatter>();
    static {
        formatters.put("JSON", new JSONFormatter());
        formatters.put("XML", new XMLFormatter());
        formatters.put("YAML", new YAMLFormatter());
    };
    private JPanel contentPane;

    private JTextArea srcTextArea;
    private JTextArea toTextArea;
    private JButton convertButton;
    private JComboBox fmtComboBox;
    private JTextArea lineNumTextArea;
    private int errorLine = -1;

    /**
     * Create the frame.
     */
    public MainFrame() {
        super("jFormatter");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMainLayout();
        createSrcPanel();
        createControlPanel();
        createToPanel();

        addEvent();
    }

    private void addEvent() {
        addSrcTextAreaEvent();
        addConvertButtonEvent();
    }

    private void addConvertButtonEvent() {
        convertButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                String fromText = srcTextArea.getText();
                String txtFormat = fmtComboBox.getSelectedItem().toString();
                if (null != fromText && fromText.trim().length() > 0) {
                    try {
                        Formatter formatter = formatters.get(txtFormat);
                        toTextArea.setText(formatter.format(fromText));
                    } catch (FormartException e) {
                        errorLine = e.getLineNumber();
                        try {
                            int startIndex = srcTextArea.getLineStartOffset(errorLine);
                            int endIndex = srcTextArea.getLineEndOffset(errorLine);
                            Highlighter.HighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(Color.RED);
                            srcTextArea.getHighlighter().addHighlight(startIndex, endIndex, painter);
                        } catch (BadLocationException e1) {
                            // ignore
                        }
                        toTextArea.setText(e.getMessage());
                        toTextArea.setLineWrap(true);
                    } catch (Exception e) {
                        toTextArea.setText(e.getMessage());
                        toTextArea.setLineWrap(true);
                    }
                }
            }
        });
    }

    private void addSrcTextAreaEvent() {
        srcTextArea.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) {
                removeHighlight();
            }

            public void removeUpdate(DocumentEvent e) {
                removeHighlight();
            }

            public void insertUpdate(DocumentEvent e) {
                removeHighlight();
            }

            public void removeHighlight() {
                if (-1 != errorLine) {
                    srcTextArea.getHighlighter().removeAllHighlights();
                }
            }
        });

        srcTextArea.getDocument().addDocumentListener(new DocumentListener(){
            public String getText(){
                int caretPosition = srcTextArea.getDocument().getLength();
                Element root = srcTextArea.getDocument().getDefaultRootElement();
                String text = "  1  " + System.getProperty("line.separator");
                for(int i = 2; i < root.getElementIndex( caretPosition ) + 2; i++){
                    text += "  " + i + "  " + System.getProperty("line.separator");
                }
                return text;
            }
            public void changedUpdate(DocumentEvent de) {
                lineNumTextArea.setText(getText());
            }
 
            public void insertUpdate(DocumentEvent de) {
                lineNumTextArea.setText(getText());
            }
 
            public void removeUpdate(DocumentEvent de) {
                lineNumTextArea.setText(getText());
            }
 
        });
    }

    private void createToPanel() {
        JScrollPane scrollPane = new JScrollPane();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridheight = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.weightx=1;
        contentPane.add(scrollPane, gbc);

        toTextArea = new JTextArea(20, 40);
        Font f = new Font(Font.SANS_SERIF, Font.PLAIN, 16);
        toTextArea.setFont(f);
        toTextArea.setMargin(new Insets(0, 10, 0, 10));
        scrollPane.setViewportView(toTextArea);
    }

    private void createControlPanel() {
        JPanel controlPanel = new JPanel();
        GridBagConstraints gbc_panel = new GridBagConstraints();
        gbc_panel.gridheight = 2;
        gbc_panel.insets = new Insets(0, 0, 5, 5);
        //gbc_panel.fill = GridBagConstraints.BOTH;
        gbc_panel.gridx = 1;
        gbc_panel.gridy = 0;
        contentPane.add(controlPanel, gbc_panel);

        GridBagLayout controlPanelLayout = new GridBagLayout();
        controlPanelLayout.columnWidths = new int[]{0, 0, 0, 0};
        controlPanelLayout.rowHeights = new int[]{0, 0, 0, 0, 0, 0};
        controlPanelLayout.columnWeights = new double[]{0.0, 0.0, 1.0, Double.MIN_VALUE};
        controlPanelLayout.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
        controlPanel.setLayout(controlPanelLayout);

        fmtComboBox = new JComboBox(formatters.keySet().toArray());
        fmtComboBox.setBorder(BorderFactory.createTitledBorder("Text Format"));
        GridBagConstraints gbcComboBox = new GridBagConstraints();
        gbcComboBox.insets = new Insets(0, 0, 5, 0);
        gbcComboBox.fill = GridBagConstraints.HORIZONTAL;
        gbcComboBox.gridx = 2;
        gbcComboBox.gridy = 0;
        gbcComboBox.weightx=0.0;
        controlPanel.add(fmtComboBox, gbcComboBox);

        convertButton = new JButton(">>");
        GridBagConstraints gbcConvertButton = new GridBagConstraints();
        gbcConvertButton.insets = new Insets(0, 0, 5, 0);
        gbcConvertButton.gridx = 2;
        gbcConvertButton.gridy = 1;
        controlPanel.add(convertButton, gbcConvertButton);
    }

    private void createSrcPanel() {
        JScrollPane scrollPane = new JScrollPane();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridheight = 2;
        gbc.insets = new Insets(0, 0, 0, 5);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx=1;
        contentPane.add(scrollPane, gbc);

        srcTextArea = new JTextArea(20, 40);
        Font f = new Font(Font.SANS_SERIF, Font.PLAIN, 16);
        srcTextArea.setFont(f);
        srcTextArea.setMargin(new Insets(0, 10, 0, 10));
        scrollPane.setViewportView(srcTextArea);

        lineNumTextArea = new JTextArea("  1  ");
        lineNumTextArea.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 1, Color.LIGHT_GRAY));
        lineNumTextArea.setEditable(false);
        lineNumTextArea.setFont(f);
        scrollPane.setRowHeaderView(lineNumTextArea);
    }

    private void setMainLayout() {
        setBounds(0, 0, 800, 600);
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        setContentPane(contentPane);
        GridBagLayout mainLayout = new GridBagLayout();

        mainLayout.columnWidths = new int[]{0, 0, 0, 0};
        mainLayout.rowHeights = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        mainLayout.columnWeights = new double[]{0.0, 0.0, 1.0, Double.MIN_VALUE};
        mainLayout.rowWeights = new double[]{1.0, 0.0, Double.MIN_VALUE};
        contentPane.setLayout(mainLayout);

        //pack();
        setVisible(true);
        setLocationRelativeTo(null);
    }

    /**
     * Launch the application.
     */
    public static void main(String[] args) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    MainFrame frame = new MainFrame();
                    frame.setVisible(true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
