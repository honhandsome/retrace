import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

public class RetraceDialog extends JDialog {
    private final Project project;
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JComboBox<String> comboRetraceBox;
    private JComboBox<String> comboMappingBox;
    private JButton buttonRetraceConfig;
    private JButton buttonMappingConfig;
    private JTextArea textArea;

    public RetraceDialog(@Nullable Project project) {
        this.project = project;
        setContentPane(contentPane);
        setMinimumSize(new Dimension(800, 600));
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        buttonRetraceConfig.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onConfig();
            }
        });

        buttonMappingConfig.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onAddMapping();
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
    }

    private void onConfig() {
        FileChooserDescriptor chooserDescriptor = new FileChooserDescriptor(true,
                true, true, true, true, true);
        VirtualFile virtualFile = FileChooser.chooseFile(chooserDescriptor, project, null);
        if (virtualFile != null) {
            comboRetraceBox.addItem(virtualFile.getPath());
        }
    }

    private void onAddMapping() {
        FileChooserDescriptor chooserDescriptor = new FileChooserDescriptor(true,
                true, true, true, true, true);
        VirtualFile virtualFile = FileChooser.chooseFile(chooserDescriptor, project, null);
        if (virtualFile != null) {
            comboMappingBox.addItem(virtualFile.getPath());
        }
    }

    private void onOK() {
        // retrace
        textArea.setText("result");
        textArea.setEditable(false);
    }

    private void onCancel() {
        dispose();
    }

    public static void main(String[] args) {
        RetraceDialog dialog = new RetraceDialog(null);
        dialog.pack();
        dialog.setVisible(true);
        System.exit(0);
    }
}
