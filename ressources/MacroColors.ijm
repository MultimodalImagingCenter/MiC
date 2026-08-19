dir = "C:/Users/cedric/OneDrive/prog/MIC-Github/MiC/ressources/";

function createColor(name, r, g, b) {
    setForegroundColor(r, g, b);
    newImage(name, "RGB black", 30, 20, 1);
    makeRectangle(2, 2, 26, 16);
    run("Fill", "slice");
    saveAs("PNG", dir + name + ".png");
    close();
}

createColor("yellow",240,228,66);
createColor("orange",230,159,0);
createColor("lightblue",86,180,233);
createColor("magenta",204,121,167);
createColor("turquoise",0,158,115);
createColor("blue",0,0,240);
createColor("purple",117,112,179);
createColor("brown",140,81,10);
createColor("royalblue",65,105,180);
createColor("grey",128,128,128);

newImage("black","RGB black",30,20,1);
saveAs("PNG",dir+"black.png");
close();

setForegroundColor(255,255,255);
newImage("white","RGB black",30,20,1);
makeRectangle(2,2,26,16);
run("Fill","slice");
saveAs("PNG",dir+"white.png");
close();
