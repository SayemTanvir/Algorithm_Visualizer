package org.example.VisuAlgorithm;

import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.util.Duration;

import java.util.*;

/**
 * Doubly Linked List — VisualGo-style.
 *
 * Node layout:  [◄ prev | value | next ►]
 * Forward arrows (blue)  go above the node row.
 * Backward arrows (red)  go below the node row.
 * Highlighted state colours same as singly: orange / blue / green / red / purple.
 */
public class DoublyLinkedListController {

    @FXML private Pane      canvas;
    @FXML private TextField valueField;
    @FXML private TextField indexField;
    @FXML private Label     statusLabel;
    @FXML private Label     headerStatusLabel;

    private static class Node {
        int  data; Node prev, next;
        Node(int data) { this.data = data; }
    }
    private Node head;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final double START_X = 60;
    private static final double NODE_Y  = 230;
    private static final double PREV_W  = 32;
    private static final double DATA_W  = 64;
    private static final double NEXT_W  = 32;
    private static final double NODE_H  = 50;
    private static final double TOTAL_W = PREV_W + DATA_W + NEXT_W;   // 128
    private static final double GAP     = 170;
    private static final double ARC     = 10;

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final Color C_DEFAULT   = Color.web("#0f172a");
    private static final Color C_STROKE    = Color.web("#3b82f6");
    private static final Color C_TEXT      = Color.web("#e2e8f0");
    private static final Color C_FWD       = Color.web("#2563eb");
    private static final Color C_BWD       = Color.web("#dc2626");
    private static final Color C_HIGHLIGHT = Color.web("#f97316");
    private static final Color C_TRAVERSE  = Color.web("#3b82f6");
    private static final Color C_FOUND     = Color.web("#22c55e");
    private static final Color C_NEW       = Color.web("#a855f7");

    private final Random   rng   = new Random();
    private       Timeline anim  = new Timeline();

    @FXML public void initialize() { redraw(-1, -1); }
    @FXML private void onBack()    { Launcher.switchScene("linked-list-view.fxml"); }

    // ══════════════════════════════════════════════════════════════════════════
    //  Button handlers
    // ══════════════════════════════════════════════════════════════════════════

    @FXML private void onRandom() {
        stopAnim(); head = null;
        int count = rng.nextInt(4) + 3;
        SequentialTransition seq = new SequentialTransition();
//        for (int i = 0; i < n; i++) {
//            int v = rng.nextInt(90) + 10;
//            seq.getChildren().add(pause(0.08, () -> { appendTail(v); redraw(-1,-1); }));
//        }
        Timeline timeline = new Timeline();

        for (int i = 0; i < count; i++) {
            int v = rng.nextInt(90) + 10;
            int step = i;

            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.seconds(step * 0.6), e -> {

                        Node node = new Node(v);

                        if (head == null) {
                            head = node;
                        } else {
                            Node temp = head;
                            while (temp.next != null) temp = temp.next;

                            temp.next = node;
                            node.prev = temp;
                        }

                        redraw(-1, -1);
                        setStatus("Inserted random: " + v);
                    })
            );
        }

        timeline.play();
        seq.play(); setStatus("Generated " + count + " random nodes");
    }

    @FXML private void onInsertHead() {
        Integer v = readVal(); if (v==null) return; stopAnim(); animInsert(0, v);
    }
    @FXML private void onInsertTail() {
        Integer v = readVal(); if (v==null) return; stopAnim(); animInsert(size(), v);
    }
    @FXML private void onInsertAt() {
        Integer v = readVal(); if (v==null) return;
        Integer i = readIdx(); if (i==null) return;
        if (i<0||i>size()){err("Index out of range [0.."+size()+"]");return;}
        stopAnim(); animInsert(i, v);
    }
    @FXML private void onDeleteHead() {
        if (head==null){err("List is empty");return;} stopAnim(); animDelete(0);
    }
    @FXML private void onDeleteTail() {
        if (head==null){err("List is empty");return;} stopAnim(); animDelete(size()-1);
    }
    @FXML private void onDeleteAt() {
        Integer i = readIdx(); if (i==null) return;
        if (i<0||i>=size()){err("Index out of range");return;} stopAnim(); animDelete(i);
    }
    @FXML private void onSearch() {
        Integer v = readVal(); if (v==null) return;
        if (head==null){err("List is empty");return;} stopAnim(); animSearch(v);
    }
    @FXML private void onTraverse() {
        if (head==null){err("List is empty");return;} stopAnim(); animTraverse();
    }
    @FXML private void onSort() {
        if (head==null||head.next==null){err("Need ≥ 2 nodes");return;} stopAnim(); animSort();
    }
    @FXML private void onClear() {
        stopAnim(); head=null; redraw(-1,-1); setStatus("List cleared");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Animations
    // ══════════════════════════════════════════════════════════════════════════

    private void animInsert(int idx, int val) {
        List<KeyFrame> kf = new ArrayList<>();  double t = 0;
        for (int i = 0; i < idx; i++) {
            int fi=i;
            kf.add(kfAt(t, () -> { redraw(-1,fi); setStatus("Move ptr → index "+fi); }));
            t += 0.4;
        }
        if (idx > 0) {
            kf.add(kfAt(t, () -> { redraw(idx-1, -1); setStatus("Locate predecessor at index "+(idx-1)); }));
            t += 0.65;
        }
        kf.add(kfAt(t, () -> { redraw(idx>0?idx-1:-1, -1); ghostNode(idx, val); setStatus("New node ["+val+"] ready to link"); }));
        t += 0.65;
        kf.add(kfAt(t, () -> { insertLogic(idx, val); redraw(idx, -1); setStatus("Inserted ["+val+"] at index "+idx+" ✓"); }));
        run(kf);
    }

    private void animDelete(int idx) {
        List<KeyFrame> kf = new ArrayList<>();  double t = 0;
        if(idx!=size()-1) {
            for (int i = 0; i < idx; i++) {
                int fi = i;
                kf.add(kfAt(t, () -> {
                    redraw(-1, fi);
                    setStatus("Move ptr → index " + fi);
                }));
                t += 0.4;
            }
        }
        kf.add(kfAt(t, () -> { redraw(idx,-1); setStatus("Mark for deletion: index "+idx); }));  t+=0.65;
        kf.add(kfAt(t, () -> { int v=getAt(idx).data; deleteLogic(idx); redraw(-1,-1); setStatus("Deleted ["+v+"] ✓"); }));
        run(kf);
    }

    private void animSearch(int target) {
        List<KeyFrame> kf = new ArrayList<>();  double t=0;
        Node cur=head; int i=0; int found=-1;
        while (cur!=null) {
            int fi=i;
            kf.add(kfAt(t, () -> { redraw(-1,fi); setStatus("Check index "+fi+"…"); }));  t+=0.5;
            if (cur.data==target){found=i;break;}
            cur=cur.next; i++;
        }
        if (found>=0){ int ff=found;
            kf.add(kfAt(t, () -> { redraw(ff,-1); setStatus("✓ Found ["+target+"] at index "+ff); }));
        } else {
            kf.add(kfAt(t, () -> { redraw(-1,-1); setStatus("✗ ["+target+"] not found"); }));
        }
        run(kf);
    }

    private void animTraverse() {
        List<KeyFrame> kf = new ArrayList<>();  double t=0;
        Node cur=head; int i=0;
        while(cur!=null){ int fi=i; int fv=cur.data;
            kf.add(kfAt(t,()->{redraw(-1,fi);setStatus("ptr → ["+fi+"] = "+fv);})); t+=0.5;
            cur=cur.next; i++;
        }
        int finalI = i;
        kf.add(kfAt(t,()->{redraw(-1,-1);setStatus("Traversal complete — "+ finalI +" nodes ✓");}));
        run(kf);
    }

    private void animSort() {
        List<KeyFrame> kf = new ArrayList<>();  double t=0;
        int n=size();
        for(int pass=0;pass<n-1;pass++){
            Node a=head; int ai=0;
            while(a!=null&&a.next!=null){
                Node b=a.next; int fa=ai,fb=ai+1;
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
    //  Redraw
    //    primary   = orange (target node)
    //    secondary = blue   (traversal cursor / compare B)
    // ══════════════════════════════════════════════════════════════════════════
    private void redraw(int primary, int secondary) {
        canvas.getChildren().clear();
        int sz=size();
        canvas.setPrefWidth(Math.max(900, START_X+sz*GAP+200));
        canvas.setPrefHeight(560);

        if (head==null) {
            Text t=new Text(350,180,"Doubly Linked List is empty");
            t.setFont(Font.font("Segoe UI",20)); t.setFill(Color.web("#94a3b8"));
            canvas.getChildren().add(t); return;
        }

        Node cur=head; int idx=0;
        while(cur!=null){
            double x=START_X+idx*GAP;
            Color col = (idx==primary)?C_HIGHLIGHT:(idx==secondary)?C_TRAVERSE:C_DEFAULT;
            drawNode(x, NODE_Y, cur.data, col, idx);

            if(cur.next!=null){
                // Forward arrow (above)
                double ax=x+TOTAL_W, bx=x+GAP, ay=NODE_Y+14;
                drawArrow(ax,ay,bx,ay,C_FWD,true);
                // Backward arrow (below)
                double by2=NODE_Y+NODE_H-14;
                drawArrow(bx,by2,ax,by2,C_BWD,false);
            } else {
                // NULL + TAIL
                Text n=new Text(x+TOTAL_W+6,NODE_Y+NODE_H/2.0+6,"null");
                n.setFont(Font.font("Monospace",13)); n.setFill(Color.web("#ef4444"));
                canvas.getChildren().add(n);
                addLabel(x+DATA_W/2.0+PREV_W-14,NODE_Y+NODE_H+28,"TAIL","#ef4444",12);
            }

            if(idx==0){
                // NULL on left
                Text n=new Text(START_X-46,NODE_Y+NODE_H/2.0+6,"null");
                n.setFont(Font.font("Monospace",13)); n.setFill(Color.web("#ef4444"));
                canvas.getChildren().add(n);
                addLabel(x+PREV_W+DATA_W/2.0-14,NODE_Y-30,"HEAD","#22c55e",12);
            }

            cur=cur.next; idx++;
        }
    }

    private void drawNode(double x, double y, int val, Color color, int idx) {
        boolean def = color.equals(C_DEFAULT);

        // Prev cell
        Rectangle pv=new Rectangle(x,y,PREV_W,NODE_H);
        style(pv, C_DEFAULT, def?C_STROKE:color);
        // Data cell
        Rectangle dc=new Rectangle(x+PREV_W,y,DATA_W,NODE_H);
        style(dc, def?C_DEFAULT:color.deriveColor(0,1,1,0.22), def?C_STROKE:color);
        if(!def) dc.setEffect(new DropShadow(16,color));
        // Next cell
        Rectangle nx=new Rectangle(x+PREV_W+DATA_W,y,NEXT_W,NODE_H);
        style(nx, C_DEFAULT, def?C_STROKE:color);

        // ◄ dot in prev cell
        Circle dp=new Circle(x+PREV_W/2.0,y+NODE_H/2.0,4, def?C_STROKE:color);
        // Value text
        Text tv=new Text(x+PREV_W+DATA_W/2.0-10,y+NODE_H/2.0+7,String.valueOf(val));
        tv.setFont(Font.font("Segoe UI",FontWeight.BOLD,17));
        tv.setFill(def?C_TEXT:color);
        // ► dot in next cell
        Circle dn=new Circle(x+PREV_W+DATA_W+NEXT_W/2.0,y+NODE_H/2.0,4, def?C_STROKE:color);

        // Index
        Text idxT=new Text(x+PREV_W+DATA_W/2.0-8,y-8,"["+idx+"]");
        idxT.setFont(Font.font("Segoe UI",11)); idxT.setFill(Color.web("#94a3b8"));

        canvas.getChildren().addAll(pv,dc,nx,dp,tv,dn,idxT);
    }

    private void style(Rectangle r, Color fill, Color stroke){
        r.setArcWidth(ARC); r.setArcHeight(ARC);
        r.setFill(fill); r.setStroke(stroke); r.setStrokeWidth(2.2);
    }

    private void drawArrow(double x1,double y1,double x2,double y2,Color color,boolean rightHead){
        Line ln=new Line(x1,y1,x2,y2);
        ln.setStroke(color); ln.setStrokeWidth(2);
        canvas.getChildren().add(ln);
        Polygon p=new Polygon();
        if(rightHead) p.getPoints().addAll(x2,y2,x2-9,y2-5,x2-9,y2+5);
        else          p.getPoints().addAll(x2,y2,x2+9,y2-5,x2+9,y2+5);
        p.setFill(color);
        canvas.getChildren().add(p);
        ln.toBack();
        p.toBack();
    }

    private void ghostNode(int idx, int val) {
        redraw(-1,-1);
        double x=START_X+idx*GAP, y=NODE_Y-80;
        drawNode(x,y,val,C_NEW,idx);
    }

    private void addLabel(double x,double y,String txt,String hex,int size){
        Text t=new Text(x,y,txt);
        t.setFont(Font.font("Segoe UI",FontWeight.BOLD,size));
        t.setFill(Color.web(hex));
        canvas.getChildren().add(t);
    }

    // ── Data model ────────────────────────────────────────────────────────────
    private void insertLogic(int idx, int val){
        Node n=new Node(val);
        if(idx==0){n.next=head;if(head!=null)head.prev=n;head=n;return;}
        Node t=head; for(int i=0;i<idx-1&&t.next!=null;i++)t=t.next;
        n.next=t.next; n.prev=t;
        if(t.next!=null)t.next.prev=n; t.next=n;
    }
    private void deleteLogic(int idx){
        if(idx==0){head=head.next;if(head!=null)head.prev=null;return;}
        Node t=head; for(int i=0;i<idx-1&&t.next!=null;i++)t=t.next;
        Node del=t.next; if(del==null)return;
        t.next=del.next; if(del.next!=null)del.next.prev=t;
    }
    private void appendTail(int val){
        Node n=new Node(val);
        if(head==null){head=n;return;}
        Node t=head; while(t.next!=null)t=t.next; t.next=n; n.prev=t;
    }
    private Node getAt(int idx){ Node t=head; for(int i=0;i<idx;i++)t=t.next; return t; }
    private int size(){ int c=0; Node t=head; while(t!=null){c++;t=t.next;} return c; }

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