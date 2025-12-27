#!/bin/bash

# Quota Bar - 构建脚本
# 用于编译 VS Code 扩展和 IntelliJ IDEA 插件

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目根目录
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
VSCODE_DIR="$PROJECT_ROOT/vscode-extension"
IDEA_DIR="$PROJECT_ROOT/idea-plugin"
OUT_DIR="$PROJECT_ROOT/out"

# 打印带颜色的消息
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 确保输出目录存在
ensure_out_dir() {
    if [ ! -d "$OUT_DIR" ]; then
        mkdir -p "$OUT_DIR"
        print_info "创建输出目录: $OUT_DIR"
    fi
}

# 检查 Java 17
check_java() {
    # 尝试使用系统 Java 17
    if [ -d "/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home" ]; then
        export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home"
    elif [ -d "$HOME/Library/Java/JavaVirtualMachines/jbr-17.0.14/Contents/Home" ]; then
        export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/jbr-17.0.14/Contents/Home"
    else
        # 尝试通过 java_home 查找
        JAVA17_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || true)
        if [ -n "$JAVA17_HOME" ]; then
            export JAVA_HOME="$JAVA17_HOME"
        else
            print_error "未找到 Java 17，IDEA 插件编译将跳过"
            return 1
        fi
    fi
    print_info "使用 Java: $JAVA_HOME"
    return 0
}

# 编译 VS Code 扩展
build_vscode() {
    print_info "========================================="
    print_info "编译 VS Code 扩展..."
    print_info "========================================="
    
    cd "$VSCODE_DIR"
    
    # 检查 node_modules
    if [ ! -d "node_modules" ]; then
        print_info "安装依赖..."
        npm install
    fi
    
    # 编译
    print_info "编译 TypeScript..."
    npm run compile
    
    print_success "VS Code 扩展编译成功！"
}

# 打包 VS Code 扩展
package_vscode() {
    print_info "打包 VS Code 扩展..."
    
    cd "$VSCODE_DIR"
    
    # 删除旧的 vsix 文件
    rm -f *.vsix
    
    # 检查 vsce
    if ! command -v vsce &> /dev/null; then
        print_warning "vsce 未安装，尝试使用 npx..."
        npx -y vsce package
    else
        vsce package
    fi
    
    # 复制到输出目录
    ensure_out_dir
    cp -f *.vsix "$OUT_DIR/"
    
    print_success "VS Code 扩展打包成功！"
    print_info "输出文件: $OUT_DIR/$(ls *.vsix)"
}

# 编译 IDEA 插件
build_idea() {
    print_info "========================================="
    print_info "编译 IntelliJ IDEA 插件..."
    print_info "========================================="
    
    cd "$IDEA_DIR"
    
    # 检查 gradlew
    if [ ! -f "gradlew" ]; then
        print_info "生成 Gradle Wrapper..."
        gradle wrapper
    fi
    
    # 确保 gradlew 可执行
    chmod +x gradlew
    
    # 编译
    print_info "编译 Kotlin..."
    ./gradlew compileKotlin --warning-mode=none
    
    print_success "IDEA 插件编译成功！"
}

# 打包 IDEA 插件
package_idea() {
    print_info "打包 IDEA 插件..."
    
    cd "$IDEA_DIR"
    
    ./gradlew buildPlugin --warning-mode=none
    
    # 复制到输出目录
    ensure_out_dir
    cp -f build/distributions/*.zip "$OUT_DIR/" 2>/dev/null || true
    
    print_success "IDEA 插件打包成功！"
    
    # 显示输出的文件
    local zip_file=$(ls build/distributions/*.zip 2>/dev/null | head -1)
    if [ -n "$zip_file" ]; then
        local filename=$(basename "$zip_file")
        print_info "输出文件: $OUT_DIR/$filename"
    fi
}

# 清理
clean() {
    print_info "清理构建产物..."
    
    # 清理 VS Code
    if [ -d "$VSCODE_DIR/out" ]; then
        rm -rf "$VSCODE_DIR/out"
        print_info "已清理: $VSCODE_DIR/out"
    fi
    rm -f "$VSCODE_DIR"/*.vsix
    
    # 清理 IDEA
    if [ -d "$IDEA_DIR/build" ]; then
        rm -rf "$IDEA_DIR/build"
        print_info "已清理: $IDEA_DIR/build"
    fi
    
    # 清理输出目录
    if [ -d "$OUT_DIR" ]; then
        rm -rf "$OUT_DIR"
        print_info "已清理: $OUT_DIR"
    fi
    
    print_success "清理完成！"
}

# 显示帮助
show_help() {
    echo "Quota Bar 构建脚本"
    echo ""
    echo "用法: $0 [命令]"
    echo ""
    echo "命令:"
    echo "  all       编译所有项目 (默认)"
    echo "  vscode    仅编译 VS Code 扩展"
    echo "  idea      仅编译 IDEA 插件"
    echo "  package   编译并打包所有项目"
    echo "  clean     清理构建产物"
    echo "  help      显示此帮助信息"
    echo ""
    echo "输出目录: $OUT_DIR"
    echo ""
    echo "示例:"
    echo "  $0              # 编译所有项目"
    echo "  $0 vscode       # 仅编译 VS Code 扩展"
    echo "  $0 package      # 编译并打包所有项目"
}

# 主函数
main() {
    local command="${1:-all}"
    
    echo ""
    print_info "Quota Bar 构建脚本"
    print_info "项目根目录: $PROJECT_ROOT"
    print_info "输出目录: $OUT_DIR"
    echo ""
    
    case "$command" in
        all)
            build_vscode
            echo ""
            if check_java; then
                build_idea
            fi
            echo ""
            print_success "========================================="
            print_success "所有项目编译完成！"
            print_success "========================================="
            ;;
        vscode)
            build_vscode
            ;;
        idea)
            if check_java; then
                build_idea
            else
                exit 1
            fi
            ;;
        package)
            build_vscode
            package_vscode
            echo ""
            if check_java; then
                build_idea
                package_idea
            fi
            echo ""
            print_success "========================================="
            print_success "所有项目打包完成！"
            print_success "输出目录: $OUT_DIR"
            print_success "========================================="
            echo ""
            print_info "打包产物:"
            ls -la "$OUT_DIR"
            ;;
        clean)
            clean
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            print_error "未知命令: $command"
            echo ""
            show_help
            exit 1
            ;;
    esac
}

# 运行主函数
main "$@"
