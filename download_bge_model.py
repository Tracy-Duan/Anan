"""
下载正确的 BGE small zh v1.5 ONNX 模型
"""
from huggingface_hub import snapshot_download
import os
import shutil

print("=" * 60)
print("开始下载 BGE small zh v1.5 ONNX 模型")
print("=" * 60)

# 方法 1: 尝试从 Xenova 仓库下载（通常有优化过的 ONNX 版本）
try:
    print("\n[方法 1] 从 Xenova/bge-small-zh-v1.5 下载...")
    model_dir = snapshot_download(
        repo_id="Xenova/bge-small-zh-v1.5",
        local_dir="./downloaded_bge_model",
        ignore_patterns=["*.bin", "*.safetensors"]  # 只下载 ONNX 相关文件
    )
    print(f"✅ 下载成功: {model_dir}")
    
    # 查找 ONNX 文件
    onnx_files = []
    for root, dirs, files in os.walk(model_dir):
        for file in files:
            if file.endswith('.onnx'):
                full_path = os.path.join(root, file)
                size_mb = os.path.getsize(full_path) / (1024 * 1024)
                onnx_files.append((full_path, size_mb))
                print(f"  找到: {file} ({size_mb:.2f} MB)")
    
    if onnx_files:
        # 选择最大的 ONNX 文件（通常是完整模型）
        best_model = max(onnx_files, key=lambda x: x[1])
        print(f"\n✅ 推荐模型: {os.path.basename(best_model[0])} ({best_model[1]:.2f} MB)")
        
        # 复制到项目
        dest_path = "./bge-small-zh-v1.5.onnx"
        shutil.copy2(best_model[0], dest_path)
        print(f"✅ 已复制到: {dest_path}")
        print(f"\n请将此文件替换到:")
        print(f"  D:\\JavaPeoject\\anan\\app\\src\\main\\assets\\models\\bge-small-zh-v1.5.onnx")
    else:
        print("❌ 未找到 ONNX 文件")
        
except Exception as e:
    print(f"❌ Xenova 下载失败: {e}")
    print("\n[方法 2] 尝试从 BAAI 官方仓库下载...")
    
    try:
        # 方法 2: 从官方仓库下载并转换
        from transformers import AutoModel, AutoTokenizer
        from optimum.onnxruntime import ORTModelForFeatureExtraction
        
        print("正在下载官方模型（可能需要几分钟）...")
        model_name = "BAAI/bge-small-zh-v1.5"
        
        # 导出为 ONNX
        print("正在转换为 ONNX 格式...")
        ort_model = ORTModelForFeatureExtraction.from_pretrained(
            model_name, 
            export=True,
            from_transformers=True
        )
        
        # 保存
        output_dir = "./bge-small-zh-v1.5-onnx-official"
        ort_model.save_pretrained(output_dir)
        
        print(f"✅ ONNX 模型已保存到: {output_dir}")
        
        # 查找并复制
        for file in os.listdir(output_dir):
            if file.endswith('.onnx'):
                src = os.path.join(output_dir, file)
                size_mb = os.path.getsize(src) / (1024 * 1024)
                print(f"  模型文件: {file} ({size_mb:.2f} MB)")
                
                dest = "./bge-small-zh-v1.5.onnx"
                shutil.copy2(src, dest)
                print(f"✅ 已复制到: {dest}")
                break
        
        print(f"\n请将此文件替换到:")
        print(f"  D:\\JavaPeoject\\anan\\app\\src\\main\\assets\\models\\bge-small-zh-v1.5.onnx")
        
    except ImportError:
        print("❌ 需要安装 optimum: pip install optimum[onnx]")
    except Exception as e2:
        print(f"❌ 官方模型转换失败: {e2}")

print("\n" + "=" * 60)
print("下载完成！")
print("=" * 60)
