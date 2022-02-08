import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;

import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class RetraceDialog extends JDialog {
    private final Project project;
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JComboBox<String> comboMappingBox;
    private JButton buttonMappingConfig;
    private JTextArea textArea;
    private JButton dumpCrashButton;
    private Thread worker;

    public RetraceDialog(@Nullable Project project) {
        this.project = project;
        setContentPane(contentPane);
        setMinimumSize(new Dimension(800, 600));
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        buttonMappingConfig.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onAddMapping();
            }
        });

        dumpCrashButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onDumpCrash();
            }
        });

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        // textArea 自动换行
        textArea.setLineWrap(true);
        // textArea 换行不断字
        textArea.setWrapStyleWord(true);
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                if (e.getLength() == 0) return;
                dumpCrashButton.setVisible(false);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                if (e.getLength() > 0) return;
                dumpCrashButton.setVisible(true);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });

        if (project == null) return;
        //获取 project 级别的 PropertiesComponent，指定相应的 project
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(project);
        String mappingConfig = propertiesComponent.getValue("MappingConfig");
        if (mappingConfig != null && mappingConfig.length() > 0) {
            comboMappingBox.insertItemAt(mappingConfig, 0);
            comboMappingBox.setSelectedIndex(0);
        }
    }

    private void onAddMapping() {
        FileChooserDescriptor chooserDescriptor = new FileChooserDescriptor(true,
                false, false, false, false, false);
        VirtualFile virtualFile = FileChooser.chooseFile(chooserDescriptor, project, null);
        if (virtualFile != null) {
            String mappingConfig = virtualFile.getPath();
            //获取 project 级别的 PropertiesComponent，指定相应的 project
            PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(project);
            propertiesComponent.setValue("MappingConfig", mappingConfig);
            comboMappingBox.insertItemAt(mappingConfig, 0);
            comboMappingBox.setSelectedIndex(0);
        }
    }

    private void onDumpCrash() {
        new Thread(() -> {
            try {
                String result = dumpCrash();
                if (result.contains("waiting for device")) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        Messages.showMessageDialog(result, "ERROR", Messages.getInformationIcon());
                    });
                    return;
                }
                if (result.contains("Dump Failed")) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        Messages.showMessageDialog(result, "ERROR", Messages.getInformationIcon());
                    });
                    return;
                }
                ApplicationManager.getApplication().invokeLater(() -> {
                    textArea.setText(result);
                });
            } catch (Exception e) {
                e.printStackTrace();
                ApplicationManager.getApplication().invokeLater(() -> {
                    Messages.showMessageDialog(e.getMessage(), "ERROR", Messages.getInformationIcon());
                });
            }
        }).start();
    }

    private String dumpCrash() throws Exception {
        String source = TerminalHelper.execCmd("adb logcat -v raw -s AndroidRuntime --buffer=crash", 1000);
        if (source.contains("waiting for device")) return source;
        if (!source.contains("Process: com.tyjh.lightchain")) return "Dump Failed !";
        String[] crashStacks = source.split("(?=Process: com.tyjh.lightchain)");
        String result = crashStacks[crashStacks.length - 1];
        System.out.println(result);
        return result;
    }

    private void onOK() {
        if (comboMappingBox.getSelectedItem() == null) {
            Messages.showMessageDialog("Please choose Mapping first !", "ERROR", Messages.getInformationIcon());
            return;
        }
        if (textArea.getText() == null || textArea.getText().length() == 0) {
            Messages.showMessageDialog("Please add crash stack first !", "ERROR", Messages.getInformationIcon());
            return;
        }
        new Thread(() -> {
            try {
                String result = retraceImpl();
                if (result.contains("Retrace Failed")) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        Messages.showMessageDialog(result, "ERROR", Messages.getInformationIcon());
                    });
                    return;
                }
                ApplicationManager.getApplication().invokeLater(() -> {
                    textArea.setText(result);
                    textArea.setEditable(false);
                    buttonOK.setVisible(false);
                    comboMappingBox.setVisible(false);
                    buttonMappingConfig.setVisible(false);
                });
            } catch (Exception e) {
                e.printStackTrace();
                ApplicationManager.getApplication().invokeLater(() -> {
                    Messages.showMessageDialog(e.getMessage(), "ERROR", Messages.getInformationIcon());
                });
            }
        }).start();
    }

    private String retraceImpl() throws Exception {
        // 创建崩溃堆栈临时文件
        File crash = createTempFile(textArea.getText());
        if (crash != null) {
            //通过自己插件的id获取pluginId
            PluginId pluginId = PluginId.getId("com.amazingchs.plugin.id");
            IdeaPluginDescriptor plugin = PluginManager.getPlugin(pluginId);
            if (plugin != null) {
                String path = plugin.getPath().getAbsolutePath();
                System.out.println(path);
                // retrace
                String cmd = "java -cp lib/r8.jar com.android.tools.r8.retrace.Retrace " + comboMappingBox.getSelectedItem() + " " + crash.getAbsolutePath();
                String result = TerminalHelper.execCmd(cmd, plugin.getPath());
                System.out.println(result);
                return result;
            }
        }
        return "Retrace Failed !";
    }

    private void onCancel() {
        dispose();
    }

    private File createTempFile(String content) {
        File tempFile = null;
        try {
            // 在默认临时文件路径下创建临时文件"lightchain_crash_xxx..."
            tempFile = File.createTempFile("lightchain_crash_", null, null);
            System.out.println(tempFile.getAbsolutePath());
            FileWriter fileWriter = new FileWriter(tempFile);
            PrintWriter printWriter = new PrintWriter(fileWriter);
            printWriter.println(content);
            printWriter.close();
            // 程序退出时自动删除临时文件
            tempFile.deleteOnExit();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        return tempFile;
    }

    public static void main(String[] args) {
        RetraceDialog dialog = new RetraceDialog(null);
        dialog.pack();
        dialog.setVisible(true);
        System.exit(0);
    }
}
