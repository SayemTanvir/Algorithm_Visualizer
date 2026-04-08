package org.example.VisuAlgorithm;

import java.util.Random;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.SequentialTransition;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.QuadCurve;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.util.ArrayList;
import java.util.List;

public class CircularLinkedListController {

    @FXML private Pane canvas;
    @FXML private TextField valueField;
    @FXML private TextField indexField;
    @FXML private Label statusLabel;
    @FXML private Label headerStatusLabel;

    private static class Node {
        int data; Node next;
        Node(int data){ this.data=data; }
    }
    private Node head;

    private final Random rng  = new Random();
    private Timeline anim = new Timeline();

    @FXML public void initialize(){ redraw(-1,-1); }
    @FXML private void onBack(){ stopAnim(); Launcher.switchScene("linked-list-view.fxml"); }

    // ══════════════════════════════════════════════════════════════════════════
    //  Button handlers
    // ══════════════════════════════════════════════════════════════════════════

    @FXML private void onRandom(){
        stopAnim(); head=null;
        int n=rng.nextInt(4)+3;
        SequentialTransition seq = new SequentialTransition();

        Timeline timeline = new Timeline();
        for (int i = 0; i < n; i++) {
            int value=rng.nextInt(90)+10;
            int step = i;

            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.seconds(step * 0.6), e -> {
                        Node node = new Node(value);
                        if (head == null) {
                            head = node;
                            node.next = head;
                        } else {
                            Node tail = getTail();
                            tail.next = node;
                            node.next = head;
                        }
                        redraw(-1, -1);
                        setStatus("Inserted random: " + value);
                    })
            );
        }

        timeline.play();
        seq.play(); setStatus("Generated "+n+" random nodes");
    }

    @FXML private void onInsertHead(){
        Integer v=readVal();if(v==null)return; stopAnim(); animInsert(0,v);
    }
    @FXML private void onInsertTail(){
        Integer v=readVal();if(v==null)return; stopAnim(); animInsert(size(),v);
    }
    @FXML private void onInsertAt(){
        Integer v=readVal();if(v==null)return;
        Integer i=readIdx();if(i==null)return;
        if(i<0||i>size()){err("Index out of range [0.."+size()+"]");return;}
        stopAnim(); animInsert(i,v);
    }
    @FXML private void onDeleteHead(){
        if(head==null){err("List is empty");return;} stopAnim(); animDelete(0);
    }
    @FXML private void onDeleteTail(){
        if(head==null){err("List is empty");return;} stopAnim(); animDelete(size()-1);
    }
    @FXML private void onDeleteAt(){
        Integer i=readIdx();if(i==null)return;
        if(i<0||i>=size()){err("Index out of range");return;} stopAnim(); animDelete(i);
    }
    @FXML private void onSearch(){
        Integer v=readVal();if(v==null)return;
        if(head==null){err("List is empty");return;} stopAnim(); animSearch(v);
    }
    @FXML private void onTraverse(){
        if(head==null){err("List is empty");return;} stopAnim(); animTraverse();
    }
    @FXML private void onSort(){
        if(head==null||head.next==head){err("Need ≥ 2 nodes");return;} stopAnim(); animSort();
    }
    @FXML private void onClear(){
        stopAnim(); head=null; redraw(-1,-1); setStatus("List cleared");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Animations
    // ══════════════════════════════════════════════════════════════════════════

    private void animInsert(int idx, int val){
        List<KeyFrame> kf=new ArrayList<>(); double t=0;
        for(int i=0;i<idx;i++){
            int fi=i;
            kf.add(kfAt(t,()->{redraw(-1,fi);setStatus("ptr → index "+fi);})); t+=0.4;
        }
        if(idx>0){
            kf.add(kfAt(t,()->{redraw(idx-1,-1);setStatus("Locate predecessor at index "+(idx-1));})); t+=0.65;
        }
        kf.add(kfAt(t,()->{
            if(idx>0)redraw(idx-1,-1); else redraw(-1,-1);
            ghostNode(val); // Draw temporary floating node in center
            setStatus("New node ["+val+"] ready to link");
        })); t+=0.65;
        kf.add(kfAt(t,()->{insertLogic(idx,val);redraw(idx,-1);setStatus("Inserted ["+val+"] at index "+idx+" ✓");}));
        run(kf);
    }

    private void animDelete(int idx){
        List<KeyFrame> kf=new ArrayList<>(); double t=0;
        for(int i=0;i<idx;i++){
            int fi=i;
            kf.add(kfAt(t,()->{redraw(-1,fi);setStatus("ptr → index "+fi);})); t+=0.4;
        }
        kf.add(kfAt(t,()->{redraw(idx,-1);setStatus("Mark for deletion: index "+idx);})); t+=0.65;
        kf.add(kfAt(t,()->{
            int v=getAt(idx).data; deleteLogic(idx); redraw(-1,-1);
            setStatus("Deleted ["+v+"] ✓");
        }));
        run(kf);
    }

    private void animSearch(int target){
        List<KeyFrame> kf=new ArrayList<>(); double t=0;
        Node cur=head; int i=0; int found=-1;
        do{
            int fi=i;
            kf.add(kfAt(t,()->{redraw(-1,fi);setStatus("Check index "+fi+"…");})); t+=0.5;
            if(cur.data==target){found=i;break;}
            cur=cur.next; i++;
        } while(cur!=head);

        if(found>=0){int ff=found;
            kf.add(kfAt(t,()->{redraw(ff,-1);setStatus("✓ Found ["+target+"] at index "+ff);}));
        } else {
            kf.add(kfAt(t,()->{redraw(-1,-1);setStatus("✗ ["+target+"] not found");}));
        }
        run(kf);
    }

    private void animTraverse(){
        List<KeyFrame> kf=new ArrayList<>(); double t=0;
        Node cur=head; int i=0;
        do{
            int fi=i; int fv=cur.data;
            kf.add(kfAt(t,()->{redraw(-1,fi);setStatus("ptr → ["+fi+"] = "+fv);})); t+=0.5;
            cur=cur.next; i++;
        } while(cur!=head);

        // Highlight the back-arc (returns to head)
        kf.add(kfAt(t,()->{
            redraw(-1,0,true);   // highlight HEAD and loop-back arrow with traversal color
            setStatus("Circular link → back to HEAD");
        })); t+=0.6;

        int finalI = i;
        kf.add(kfAt(t,()->{redraw(-1,-1);setStatus("Traversal complete — "+ finalI +" nodes ✓");}));
        run(kf);
    }

    private void animSort(){
        List<KeyFrame> kf=new ArrayList<>(); double t=0;
        int n=size();
        for(int pass=0;pass<n-1;pass++){
            Node a=head; int ai=0;
            for(int j=0;j<n-1;j++){
                Node b=a.next; int fa=ai,fb=(ai+1)%n;
                kf.add(kfAt(t,()->{redraw(fa,fb);setStatus("Compare ["+fa+"] vs ["+fb+"]");})); t+=0.38;
                if(a.data>b.data){
                    int tmp=a.data;a.data=b.data;b.data=tmp;
                    kf.add(kfAt(t,()->{redraw(fa,fb);setStatus("Swap ["+fa+"] ↔ ["+fb+"]");})); t+=0.38;
                }
                a=a.next; ai++;
            }
        }
        kf.add(kfAt(t,()->{redraw(-1,-1);setStatus("Sort complete ✓");}));
        run(kf);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Redraw & UI Visuals
    // ══════════════════════════════════════════════════════════════════════════

    private void redraw(int primary, int secondary){ redraw(primary, secondary, false); }

    private void redraw(int primaryIndex, int secondaryIndex, boolean arcHighlight) {
        canvas.getChildren().clear();
        canvas.setPrefWidth(1400);
        canvas.setPrefHeight(900);

        int size = size();

        if (head == null) {
            Text t = new Text(350, 180, "Circular Linked List is empty");
            t.setFont(Font.font("System", 22));
            t.setFill(Color.web("#64748b"));
            canvas.getChildren().add(t);
            return;
        }

        double centerX = 600;
        double centerY = 380;
        double radius  = 200;
        double nodeR   = 38;

        // Background guide ring
        Circle ring = new Circle(centerX, centerY, radius);
        ring.setFill(Color.TRANSPARENT);
        ring.setStrokeWidth(2);
        canvas.getChildren().add(ring);

        double[] nx = new double[size];
        double[] ny = new double[size];
        for (int i = 0; i < size; i++) {
            double angle = 2 * Math.PI * i / size - Math.PI / 2;
            nx[i] = centerX + radius * Math.cos(angle);
            ny[i] = centerY + radius * Math.sin(angle);
        }

        // ── draw arrows first (so circles sit on top) ──
        if (size == 1) {
            QuadCurve loop = new QuadCurve();
            loop.setStartX(nx[0] + nodeR * 0.6);
            loop.setStartY(ny[0] + nodeR * 0.6);
            loop.setControlX(nx[0] + 80);
            loop.setControlY(ny[0] + 90);
            loop.setEndX(nx[0] - nodeR * 0.6);
            loop.setEndY(ny[0] + nodeR * 0.6);
            loop.setFill(null);
            loop.setStroke(Color.web(arcHighlight ? "#3b82f6" : "#38bdf8", 0.8));
            loop.setStrokeWidth(2.5);
            canvas.getChildren().add(loop);
        } else {
            for (int i = 0; i < size; i++) {
                int next = (i + 1) % size;
                boolean highlightThisArrow = (arcHighlight && i == size - 1);
                String arrowColor = highlightThisArrow ? "#3b82f6" : "#38bdf8"; // Traverse Blue if looping back
                drawArrowBetweenNodes(nx[i], ny[i], nx[next], ny[next], nodeR, arrowColor);
            }
        }

        // ── draw nodes ──
        Node temp = head;
        for (int i = 0; i < size; i++) {
            double x = nx[i];
            double y = ny[i];

            // Setup colors based on selection status
            // ONLY the fill changes. The outline remains cyan (#38bdf8).
            String fillHex = "#15152c";
            String strokeHex = "#38bdf8"; // Always Default Cyan

            if (i == secondaryIndex) {
                fillHex = "#3b82f6"; // Traverse fill (Blue)
            }
            if (i == primaryIndex) {
                fillHex = "#f97316"; // Highlight fill (Orange)
            }

            Circle circle = new Circle(x, y, nodeR);
            circle.setFill(Color.web(fillHex));
            circle.setStroke(Color.web(strokeHex));
            circle.setStrokeWidth(3.0);
            circle.setEffect(new DropShadow(15, Color.web(strokeHex, 0.5))); // Glow matches the cyan outline

            String valStr = String.valueOf(temp.data);
            Text t = new Text(x - (valStr.length() > 1 ? 10 : 6), y + 7, valStr);
            t.setFont(Font.font("System", FontWeight.BOLD, 18));
            t.setFill(Color.WHITE);
            canvas.getChildren().addAll(circle, t);

            // ── labels pushed outside the ring ──
            double angle    = 2 * Math.PI * i / size - Math.PI / 2;
            double labelDist = radius + nodeR + 24;
            double lx = centerX + labelDist * Math.cos(angle);
            double ly = centerY + labelDist * Math.sin(angle);

            Text idxText = new Text(lx - 8, ly + 5, "[" + i + "]");
            idxText.setFont(Font.font("System", 13));
            idxText.setFill(Color.web("#93c5fd"));
            canvas.getChildren().add(idxText);

            if (i == 0) {
                Text headText = new Text(lx - 20, ly - 14, "HEAD");
                headText.setFont(Font.font("System", 14));
                headText.setFill(Color.web("#2dd4bf"));
                canvas.getChildren().add(headText);
            }

            if (i == size - 1 || size == 1) {
                Text tailText = new Text(lx - 16, ly + 24, "TAIL");
                tailText.setFont(Font.font("System", 14));
                tailText.setFill(Color.web("#f43f5e"));
                canvas.getChildren().add(tailText);
            }

            temp = temp.next;
        }
    }

    private void drawArrowBetweenNodes(double x1, double y1, double x2, double y2, double nodeRadius, String colorHex) {
        double dx   = x2 - x1;
        double dy   = y2 - y1;
        double dist = Math.sqrt(dx * dx + dy * dy);
        double ux   = dx / dist;
        double uy   = dy / dist;

        double sx = x1 + ux * (nodeRadius + 2);
        double sy = y1 + uy * (nodeRadius + 2);
        double ex = x2 - ux * (nodeRadius + 2);
        double ey = y2 - uy * (nodeRadius + 2);

        Line line = new Line(sx, sy, ex, ey);
        line.setStroke(Color.web(colorHex, 0.8));
        line.setStrokeWidth(2.5);
        canvas.getChildren().add(line);

        double aLen = 11;
        double aWid = 5.5;
        double px = -uy;
        double py =  ux;

        Polygon arrow = new Polygon();
        arrow.getPoints().addAll(
                ex,                              ey,
                ex - ux * aLen + px * aWid,     ey - uy * aLen + py * aWid,
                ex - ux * aLen - px * aWid,     ey - uy * aLen - py * aWid
        );
        arrow.setFill(Color.web(colorHex, 0.9));
        canvas.getChildren().add(arrow);
    }

    // Ghost node drawn directly in the center of the ring for insertion visual
    private void ghostNode(int val) {
        double centerX = 600;
        double centerY = 380;
        double nodeR = 38;

        Color strokeCol = Color.web("#38bdf8"); // Default outline (Cyan)
        Color fillCol = Color.web("#a855f7"); // NEW node fill color (Purple)

        Circle circle = new Circle(centerX, centerY, nodeR);
        circle.setFill(fillCol);
        circle.setStroke(strokeCol);
        circle.setStrokeWidth(3.0);
        circle.setEffect(new DropShadow(15, strokeCol)); // Glow matches the outline

        String valStr = String.valueOf(val);
        Text t = new Text(centerX - (valStr.length() > 1 ? 10 : 6), centerY + 7, valStr);
        t.setFont(Font.font("System", FontWeight.BOLD, 18));
        t.setFill(Color.WHITE);

        Text label = new Text(centerX - 35, centerY + nodeR + 25, "NEW NODE");
        label.setFont(Font.font("System", FontWeight.BOLD, 12));
        label.setFill(fillCol); // Text matches the purple fill

        canvas.getChildren().addAll(circle, t, label);
    }

    // ── Data model ─────────────────────────────────────────────────────────────
    private void insertLogic(int idx,int val){
        Node n=new Node(val);
        if(head==null){head=n;n.next=head;return;}
        if(idx==0){Node tail=getTail();n.next=head;tail.next=n;head=n;return;}
        Node t=head; for(int i=0;i<idx-1&&t.next!=head;i++)t=t.next;
        n.next=t.next; t.next=n;
    }
    private void deleteLogic(int idx){
        if(head==null)return;
        if(head.next==head){head=null;return;}
        Node tail=getTail();
        if(idx==0){head=head.next;tail.next=head;return;}
        Node t=head; for(int i=0;i<idx-1;i++)t=t.next;
        t.next=t.next.next;
        if(idx==size())tail.next=head; // fix if tail deleted
    }
    private Node getTail(){Node t=head;while(t.next!=head)t=t.next;return t;}
    private Node getAt(int idx){Node t=head;for(int i=0;i<idx;i++)t=t.next;return t;}
    private int size(){if(head==null)return 0;int c=0;Node t=head;do{c++;t=t.next;}while(t!=head);return c;}

    // ── Anim utilities ────────────────────────────────────────────────────────
    private KeyFrame kfAt(double s,Runnable r){return new KeyFrame(Duration.seconds(s),e->r.run());}
    private PauseTransition pause(double s,Runnable r){PauseTransition p=new PauseTransition(Duration.seconds(s));p.setOnFinished(e->r.run());return p;}
    private void run(List<KeyFrame> kf){anim=new Timeline();anim.getKeyFrames().addAll(kf);anim.play();}
    private void stopAnim(){if(anim!=null)anim.stop();}

    private Integer readVal(){try{return Integer.parseInt(valueField.getText().trim());}catch(Exception e){err("Enter a valid integer in Value");return null;}}
    private Integer readIdx(){try{return Integer.parseInt(indexField.getText().trim());}catch(Exception e){err("Enter a valid integer in Index");return null;}}
    private void err(String m){setStatus("⚠ "+m);}
    private void setStatus(String m){statusLabel.setText(m);headerStatusLabel.setText(m);}
}