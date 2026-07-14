const fs = require("fs");
const path = "F:/qiekj-android/app/build.gradle.kts";
let c = fs.readFileSync(path, "utf8");

c = c.replace(
    '?: "0.1.1"',
    '?: "0.1.3"'
);

c = c.replace(
    '?: 2',
    '?: 4'
);

fs.writeFileSync(path, c, "utf8");
console.log("version bumped to 0.1.3 (versionCode 4)");
