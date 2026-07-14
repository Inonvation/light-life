const fs = require("fs");
const path = "F:/qiekj-android/AGENTS.md";
let c = fs.readFileSync(path, "utf8");

// Use regex to match with optional \r
const pattern = /## Commit \u89c4\u8303\r?\n- `feat:` \u65b0\u529f\u80fd \/ `fix:` \u4fee\u590d \/ `refactor:` \u91cd\u6784 \/ `perf:` \u4f18\u5316 \u2192 \u51fa\u73b0\u5728 Release \u4e2d\r?\n- `chore:` \u6742\u9879 \/ `docs:` \u6587\u6863 \/ `ci:` CI \u914d\u7f6e \/ `test:` \u6d4b\u8bd5 \/ `build:` \u6784\u5efa \/ `style:` \u4ee3\u7801\u683c\u5f0f \/ `revert:` \u56de\u6eda \u2192 \u4e0d\u51fa\u73b0\u5728 Release \u4e2d\r?\n- \u524d\u7f00\u82f1\u6587\uff0c\u63cf\u8ff0\u4e2d\u6587\uff0c\u4f8b\u5982 `fix: \u4fee\u590d\u767b\u5f55\u9875\u9762\u7a7a\u6307\u9488\u5d29\u6e83`/;

const replacement = `## Commit \u89c4\u8303\r\n### \u524d\u7f00\u7c7b\u578b\r\n| \u524d\u7f00 | \u7528\u9014 | \u51fa\u73b0\u5728 Release |\r\n|------|------|:---:|\r\n| \`feat:\` | \u65b0\u529f\u80fd | \u2705 |\r\n| \`fix:\` | \u4fee\u590d Bug | \u2705 |\r\n| \`perf:\` | \u6027\u80fd\u4f18\u5316 | \u2705 |\r\n| \`refactor:\` | \u91cd\u6784\uff08\u4e0d\u5f71\u54cd\u529f\u80fd\uff09 | \u2705 |\r\n| \`chore:\` | \u6742\u9879\u3001\u4f9d\u8d56\u66f4\u65b0 | \u274c |\r\n| \`docs:\` | \u6587\u6863 | \u274c |\r\n| \`ci:\` | CI/CD \u914d\u7f6e | \u274c |\r\n| \`test:\` | \u6d4b\u8bd5 | \u274c |\r\n| \`build:\` | \u6784\u5efa\u811a\u672c | \u274c |\r\n| \`style:\` | \u4ee3\u7801\u683c\u5f0f\uff08\u65e0\u903b\u8f91\u53d8\u66f4\uff09 | \u274c |\r\n| \`revert:\` | \u56de\u6eda | \u274c |\r\n\r\n### \u57fa\u672c\u89c4\u5219\r\n- \u524d\u7f00\u82f1\u6587\u5c0f\u5199\u52a0\u5192\u53f7\uff0c\u63cf\u8ff0\u4e2d\u6587\uff0c\u4f8b\u5982 \`fix: \u4fee\u590d\u767b\u5f55\u9875\u9762\u7a7a\u6307\u9488\u5d29\u6e83\`\r\n- **\u4e00\u4e2a commit \u53ea\u505a\u4e00\u4ef6\u4e8b**\uff1a\u591a\u4e2a\u4fee\u590d\u70b9\u5fc5\u987b\u62c6\u6210\u591a\u4e2a commit\uff0c\u4e0d\u8981\u628a\u4e0d\u540c\u529f\u80fd\u7684\u6539\u52a8\u5408\u5e76\u5230\u4e00\u4e2a commit \u4e2d\r\n- \u6bcf\u4e2a commit \u5fc5\u987b\u7f16\u8bd1\u901a\u8fc7\uff0c\u4e0d\u5141\u8bb8\u63d0\u4ea4\u7f16\u8bd1\u5931\u8d25\u7684\u4ee3\u7801\r\n- \u4fdd\u6301\u7ebf\u6027\u63d0\u4ea4\u5386\u53f2\uff0c\u4e0d\u5408\u5e76\uff08squash\uff09\u6709\u4ef7\u503c\u7684\u7ec6\u7c92\u5ea6 commit`;

const result = c.replace(pattern, replacement);
if (result === c) {
    console.log("WARNING: no replacement made!");
    // Debug
    const idx = c.indexOf("## Commit");
    console.log("context:", JSON.stringify(c.substring(idx, idx + 200)));
} else {
    fs.writeFileSync(path, result, "utf8");
    console.log("AGENTS.md updated");
}
