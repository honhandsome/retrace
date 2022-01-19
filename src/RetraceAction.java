import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class RetraceAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        // 创建RetraceDialog
        RetraceDialog dialog = new RetraceDialog(event.getProject());
        // 设置dialog基于idea居中
        dialog.setLocationRelativeTo(getParentWindow(event.getProject()));
        // 设置dialog可见
        dialog.setVisible(true);
    }

    private Window getParentWindow(Project project) {
        WindowManagerEx windowManager = (WindowManagerEx) WindowManager.getInstance();
        return windowManager.suggestParentWindow(project);
    }
}
