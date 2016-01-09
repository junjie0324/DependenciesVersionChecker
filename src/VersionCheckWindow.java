import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import groovy.util.Node;
import groovy.util.XmlParser;
import groovy.xml.QName;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * バーションチェックを行うToolWindow
 */
public class VersionCheckWindow implements ToolWindowFactory {

    private static final String INPUT_AREA_HINT = "Input your 'build.gradle' script.";

    private JPanel toowWindowContent;
    private JButton versionCheckButton;
    private JTextArea inputArea;
    private JEditorPane resultArea;

    public VersionCheckWindow() {
        inputArea.setText(INPUT_AREA_HINT);
        inputArea.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent focusEvent) {
                if (inputArea.getText().equals(INPUT_AREA_HINT)) {
                    inputArea.setText("");
                }
            }

            @Override
            public void focusLost(FocusEvent focusEvent) {
                if (inputArea.getText().equals("")) {
                    inputArea.setText(INPUT_AREA_HINT);
                }
            }
        });

        versionCheckButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                String gradleScript = inputArea.getText();
                final List<String> libraryDeclarationTexts = GradleScriptParsingUtils.extractLibraryDeclarationTexts(gradleScript);
                final List<String> metaDataUrls = GradleScriptParsingUtils.createMetaDataUrls(libraryDeclarationTexts);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        List<String> latestVersions = getLatestVersions(metaDataUrls);
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append("<table>")
                                .append("<tr><th align=\"left\">Library</th><th align=\"left\">Latest version</th></tr>");
                        for (int i = 0; i < libraryDeclarationTexts.size(); i++) {
                            stringBuilder
                                    .append("<tr>")
                                    .append("<td><a href=\"" + metaDataUrls.get(i) + "\">" + libraryDeclarationTexts.get(i) + "</a></td><td>" + latestVersions.get(i) + "</td>")
                                    .append("</tr>");
                        }
                        stringBuilder.append("</table>");
                        resultArea.setText(stringBuilder.toString());

                        Notifications.Bus.notify(new Notification("versionCheckStart", "Dependencies Version Checker", "Version check finished.", NotificationType.INFORMATION));
                    }
                }).start();
            }
        });

        resultArea.addHyperlinkListener(new HyperlinkAdapter() {
            @Override
            protected void hyperlinkActivated(HyperlinkEvent hyperlinkEvent) {
                if (hyperlinkEvent.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    try {
                        Desktop.getDesktop().browse(hyperlinkEvent.getURL().toURI());
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        Content content = contentFactory.createContent(toowWindowContent, "", false);
        toolWindow.getContentManager().addContent(content);
    }

    /**
     * 最新のライブラリバーションを取得する
     *
     * @param metaDataUrls メタデータ取得用のURLリスト
     * @return
     */
    private List<String> getLatestVersions(List<String> metaDataUrls) {
        int urlNum = metaDataUrls.size();
        List<String> latestVersions = new ArrayList<String>();

        for (int i = 0; i < urlNum; i++) {
            resultArea.setText("<b>Getting latest versions (" + (i+1) + "/" + urlNum + ")</b>");
            try {
                XmlParser xmlParser = new XmlParser();
                Node node = xmlParser.parse(metaDataUrls.get(i));
                String latestVersion = node.getAt(QName.valueOf("versioning")).getAt("latest").text();
                latestVersions.add(latestVersion);
            } catch (Exception e1) {
                latestVersions.add("Not Found");
            }
        }
        return latestVersions;
    }
}
