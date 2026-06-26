(function (global) {
  'use strict';

  function merge(base, extra) {
    return Object.assign({}, base || {}, extra || {});
  }

  function itemId(item) {
    return typeof item === 'string' ? item : item && item.getModel ? String(item.getModel().id) : '';
  }

  class NodeItem {
    constructor(graph, model) {
      this.graph = graph;
      this.model = model;
      this.edges = [];
      this.states = new Set();
      this.visible = true;
    }
    getModel() { return this.model; }
    getEdges() { return this.edges; }
    isVisible() { return this.visible; }
  }

  class EdgeItem {
    constructor(graph, model, source, target) {
      this.graph = graph;
      this.model = model;
      this.source = source;
      this.target = target;
      this.states = new Set();
      this.visible = true;
    }
    getModel() { return this.model; }
    getSource() { return this.source; }
    getTarget() { return this.target; }
    isVisible() { return this.visible; }
  }

  class Graph {
    constructor(options) {
      this.options = options || {};
      this.container = typeof options.container === 'string'
        ? document.getElementById(options.container)
        : options.container;
      this.width = options.width || 800;
      this.height = options.height || 600;
      this.payload = { nodes: [], edges: [] };
      this.nodes = [];
      this.edges = [];
      this.nodeById = new Map();
      this.handlers = {};
      this.onceHandlers = {};
      this.scale = 1;
      this.offsetX = 0;
      this.offsetY = 0;
      this.drag = null;
      this.hoverItem = null;
      this.destroyed = false;
    }

    data(payload) { this.payload = payload || { nodes: [], edges: [] }; }
    changeData(payload) { this.data(payload); this.render(); }

    on(name, handler) {
      (this.handlers[name] || (this.handlers[name] = [])).push(handler);
    }

    once(name, handler) {
      (this.onceHandlers[name] || (this.onceHandlers[name] = [])).push(handler);
    }

    emit(name, event) {
      (this.handlers[name] || []).forEach(fn => fn(event));
      const once = this.onceHandlers[name] || [];
      delete this.onceHandlers[name];
      once.forEach(fn => fn(event));
    }

    render() {
      this.destroyCanvas();
      this.destroyed = false;
      this.canvas = document.createElement('canvas');
      this.canvas.style.cssText = 'display:block;width:100%;height:100%;cursor:grab;touch-action:none';
      this.canvas.width = Math.max(1, Math.round(this.width * devicePixelRatio));
      this.canvas.height = Math.max(1, Math.round(this.height * devicePixelRatio));
      this.container.appendChild(this.canvas);
      this.ctx = this.canvas.getContext('2d');
      this.ctx.setTransform(devicePixelRatio, 0, 0, devicePixelRatio, 0, 0);
      this.buildItems();
      this.layoutItems();
      this.bindEvents();
      this.fitView(this.options.fitViewPadding || 24);
      this.draw();
      setTimeout(() => {
        if (!this.destroyed) this.emit('afterlayout', {});
      }, 0);
    }

    buildItems() {
      this.nodes = (this.payload.nodes || []).map(model => new NodeItem(this, model));
      this.nodeById = new Map(this.nodes.map(item => [String(item.model.id), item]));
      this.edges = [];
      (this.payload.edges || []).forEach(model => {
        const source = this.nodeById.get(String(model.source));
        const target = this.nodeById.get(String(model.target));
        if (!source || !target) return;
        const edge = new EdgeItem(this, model, source, target);
        source.edges.push(edge);
        target.edges.push(edge);
        this.edges.push(edge);
      });
    }

    layoutItems() {
      const count = this.nodes.length;
      if (!count) return;
      const cx = this.width / 2;
      const cy = this.height / 2;
      const project = this.nodes.find(n => n.model.nodeType === 'PROJECT');
      const directories = this.nodes.filter(n => n.model.nodeType === 'DIRECTORY');
      const assigned = new Set();

      if (project) {
        project.model.x = cx;
        project.model.y = cy;
        assigned.add(project);
      }

      const dirRadius = Math.max(190, Math.min(this.width, this.height) * 0.3);
      directories.forEach((node, index) => {
        const angle = -Math.PI / 2 + index * Math.PI * 2 / Math.max(1, directories.length);
        node.model.x = cx + Math.cos(angle) * dirRadius;
        node.model.y = cy + Math.sin(angle) * dirRadius;
        assigned.add(node);
        const children = node.edges
          .filter(edge => edge.model.edgeType === 'CONTAINS')
          .map(edge => edge.source === node ? edge.target : edge.source)
          .filter(child => child.model.nodeType === 'FILE');
        const spread = Math.min(Math.PI * 1.25, 0.42 * Math.max(1, children.length));
        children.forEach((child, childIndex) => {
          const childAngle = angle - spread / 2 + spread * (childIndex + 1) / (children.length + 1);
          const radius = 105 + Math.floor(childIndex / 8) * 35;
          child.model.x = node.model.x + Math.cos(childAngle) * radius;
          child.model.y = node.model.y + Math.sin(childAngle) * radius;
          assigned.add(child);
        });
      });

      const remaining = this.nodes.filter(node => !assigned.has(node));
      const outerRadius = Math.max(280, Math.min(this.width, this.height) * 0.42);
      remaining.forEach((node, index) => {
        const angle = -Math.PI / 2 + index * Math.PI * 2 / Math.max(1, remaining.length);
        node.model.x = cx + Math.cos(angle) * outerRadius;
        node.model.y = cy + Math.sin(angle) * outerRadius;
      });

      if (count <= 300) this.relaxLayout(project);
    }

    relaxLayout(project) {
      const nodes = this.nodes;
      for (let step = 0; step < 80; step++) {
        const force = new Map(nodes.map(n => [n, { x: 0, y: 0 }]));
        for (let i = 0; i < nodes.length; i++) {
          for (let j = i + 1; j < nodes.length; j++) {
            const a = nodes[i].model;
            const b = nodes[j].model;
            let dx = a.x - b.x;
            let dy = a.y - b.y;
            const dist2 = Math.max(100, dx * dx + dy * dy);
            const dist = Math.sqrt(dist2);
            const strength = 11000 / dist2;
            dx /= dist;
            dy /= dist;
            force.get(nodes[i]).x += dx * strength;
            force.get(nodes[i]).y += dy * strength;
            force.get(nodes[j]).x -= dx * strength;
            force.get(nodes[j]).y -= dy * strength;
          }
        }
        this.edges.forEach(edge => {
          const a = edge.source.model;
          const b = edge.target.model;
          const dx = b.x - a.x;
          const dy = b.y - a.y;
          const dist = Math.max(1, Math.sqrt(dx * dx + dy * dy));
          const desired = edge.model.edgeType === 'CONTAINS' ? 110
            : edge.model.edgeType === 'PROJECT_CONTAINS' ? 210 : 180;
          const pull = (dist - desired) * 0.004;
          const fx = dx / dist * pull;
          const fy = dy / dist * pull;
          force.get(edge.source).x += fx;
          force.get(edge.source).y += fy;
          force.get(edge.target).x -= fx;
          force.get(edge.target).y -= fy;
        });
        nodes.forEach(node => {
          if (node === project) return;
          const f = force.get(node);
          node.model.x += Math.max(-8, Math.min(8, f.x));
          node.model.y += Math.max(-8, Math.min(8, f.y));
        });
      }
    }

    bindEvents() {
      const canvas = this.canvas;
      this.bound = {
        wheel: event => {
          event.preventDefault();
          const point = this.eventPoint(event);
          this.zoomAt(event.deltaY < 0 ? 1.12 : 0.89, point.x, point.y);
        },
        down: event => {
          const point = this.eventPoint(event);
          const node = this.hitNode(point.x, point.y);
          this.drag = node
            ? { type: 'node', node, startX: point.x, startY: point.y }
            : {
                type: 'canvas',
                x: point.x,
                y: point.y,
                startX: point.x,
                startY: point.y,
                ox: this.offsetX,
                oy: this.offsetY
              };
          canvas.style.cursor = 'grabbing';
        },
        move: event => {
          const point = this.eventPoint(event);
          if (this.drag) {
            if (this.drag.type === 'node') {
              const world = this.screenToWorld(point.x, point.y);
              this.drag.node.model.x = world.x;
              this.drag.node.model.y = world.y;
            } else {
              this.offsetX = this.drag.ox + point.x - this.drag.x;
              this.offsetY = this.drag.oy + point.y - this.drag.y;
            }
            this.draw();
            return;
          }
          const hit = this.hitNode(point.x, point.y) || this.hitEdge(point.x, point.y);
          if (hit !== this.hoverItem) {
            if (this.hoverItem) this.emit(this.hoverItem instanceof NodeItem ? 'node:mouseleave' : 'edge:mouseleave', { item: this.hoverItem });
            this.hoverItem = hit;
            if (hit) this.emit(hit instanceof NodeItem ? 'node:mouseenter' : 'edge:mouseenter', { item: hit });
          }
          canvas.style.cursor = hit ? 'pointer' : 'grab';
        },
        up: event => {
          const point = this.eventPoint(event);
          const dragged = this.drag;
          this.drag = null;
          canvas.style.cursor = 'grab';
          if (dragged && Math.abs((dragged.startX || point.x) - point.x) + Math.abs((dragged.startY || point.y) - point.y) > 5) return;
          const node = this.hitNode(point.x, point.y);
          if (node) return this.emit('node:click', { item: node });
          const edge = this.hitEdge(point.x, point.y);
          if (edge) return this.emit('edge:click', { item: edge });
          this.emit('canvas:click', {});
        },
        leave: () => {
          this.drag = null;
          if (this.hoverItem) {
            this.emit(this.hoverItem instanceof NodeItem ? 'node:mouseleave' : 'edge:mouseleave', { item: this.hoverItem });
            this.hoverItem = null;
          }
        }
      };
      canvas.addEventListener('wheel', this.bound.wheel, { passive: false });
      canvas.addEventListener('pointerdown', this.bound.down);
      canvas.addEventListener('pointermove', this.bound.move);
      canvas.addEventListener('pointerup', this.bound.up);
      canvas.addEventListener('pointerleave', this.bound.leave);
    }

    eventPoint(event) {
      const rect = this.canvas.getBoundingClientRect();
      return { x: event.clientX - rect.left, y: event.clientY - rect.top };
    }

    screenToWorld(x, y) {
      return { x: (x - this.offsetX) / this.scale, y: (y - this.offsetY) / this.scale };
    }

    nodeRadius(item) {
      const size = Array.isArray(item.model.size) ? item.model.size[0] : item.model.size || 20;
      return size / 2;
    }

    hitNode(x, y) {
      const point = this.screenToWorld(x, y);
      for (let i = this.nodes.length - 1; i >= 0; i--) {
        const node = this.nodes[i];
        if (!node.visible) continue;
        const dx = point.x - node.model.x;
        const dy = point.y - node.model.y;
        if (dx * dx + dy * dy <= Math.pow(this.nodeRadius(node) + 5 / this.scale, 2)) return node;
      }
      return null;
    }

    hitEdge(x, y) {
      const point = this.screenToWorld(x, y);
      for (let i = this.edges.length - 1; i >= 0; i--) {
        const edge = this.edges[i];
        if (!edge.visible || !edge.source.visible || !edge.target.visible) continue;
        const a = edge.source.model;
        const b = edge.target.model;
        const dx = b.x - a.x;
        const dy = b.y - a.y;
        const length2 = dx * dx + dy * dy || 1;
        const t = Math.max(0, Math.min(1, ((point.x - a.x) * dx + (point.y - a.y) * dy) / length2));
        const px = a.x + t * dx;
        const py = a.y + t * dy;
        if (Math.hypot(point.x - px, point.y - py) <= 6 / this.scale) return edge;
      }
      return null;
    }

    draw() {
      if (!this.ctx || this.destroyed) return;
      const ctx = this.ctx;
      ctx.setTransform(devicePixelRatio, 0, 0, devicePixelRatio, 0, 0);
      ctx.clearRect(0, 0, this.width, this.height);
      ctx.save();
      ctx.translate(this.offsetX, this.offsetY);
      ctx.scale(this.scale, this.scale);
      this.edges.forEach(edge => this.drawEdge(ctx, edge));
      this.nodes.forEach(node => this.drawNode(ctx, node));
      ctx.restore();
    }

    stateStyle(item, kind) {
      let style = merge(item.model.style);
      item.states.forEach(state => {
        const source = kind === 'node' ? this.options.nodeStateStyles : this.options.edgeStateStyles;
        style = merge(style, source && source[state]);
      });
      return style;
    }

    drawEdge(ctx, edge) {
      if (!edge.visible || !edge.source.visible || !edge.target.visible) return;
      const model = edge.model;
      const style = this.stateStyle(edge, 'edge');
      const a = edge.source.model;
      const b = edge.target.model;
      ctx.save();
      ctx.globalAlpha = style.opacity == null ? 1 : style.opacity;
      ctx.strokeStyle = style.stroke || '#87929c';
      ctx.fillStyle = style.stroke || '#87929c';
      ctx.lineWidth = (style.lineWidth || 1) / this.scale;
      ctx.setLineDash((style.lineDash || []).map(v => v / this.scale));
      ctx.beginPath();
      ctx.moveTo(a.x, a.y);
      ctx.lineTo(b.x, b.y);
      ctx.stroke();
      ctx.setLineDash([]);
      const angle = Math.atan2(b.y - a.y, b.x - a.x);
      const radius = this.nodeRadius(edge.target) + 2;
      const tx = b.x - Math.cos(angle) * radius;
      const ty = b.y - Math.sin(angle) * radius;
      const arrow = 5 / this.scale;
      ctx.beginPath();
      ctx.moveTo(tx, ty);
      ctx.lineTo(tx - Math.cos(angle - Math.PI / 6) * arrow, ty - Math.sin(angle - Math.PI / 6) * arrow);
      ctx.lineTo(tx - Math.cos(angle + Math.PI / 6) * arrow, ty - Math.sin(angle + Math.PI / 6) * arrow);
      ctx.closePath();
      ctx.fill();
      if (model.label && this.scale > 0.55) {
        const labelStyle = model.labelCfg && model.labelCfg.style || {};
        ctx.globalAlpha = 1;
        ctx.font = `${labelStyle.fontWeight || 500} ${labelStyle.fontSize || 10}px sans-serif`;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'bottom';
        ctx.lineWidth = (labelStyle.lineWidth || 0) / this.scale;
        if (labelStyle.stroke && labelStyle.lineWidth) {
          ctx.strokeStyle = labelStyle.stroke;
          ctx.strokeText(model.label, (a.x + b.x) / 2, (a.y + b.y) / 2 - 4);
        }
        ctx.fillStyle = labelStyle.fill || '#d9e7e4';
        ctx.fillText(model.label, (a.x + b.x) / 2, (a.y + b.y) / 2 - 4);
      }
      ctx.restore();
    }

    drawNode(ctx, node) {
      if (!node.visible) return;
      const model = node.model;
      const style = this.stateStyle(node, 'node');
      const radius = this.nodeRadius(node);
      ctx.save();
      ctx.globalAlpha = style.opacity == null ? (style.fillOpacity == null ? 1 : style.fillOpacity) : style.opacity;
      if (style.shadowBlur) {
        ctx.shadowBlur = style.shadowBlur / this.scale;
        ctx.shadowColor = style.shadowColor || style.stroke || '#fbbf24';
      }
      ctx.beginPath();
      ctx.arc(model.x, model.y, radius, 0, Math.PI * 2);
      ctx.fillStyle = style.fill || '#2dd4bf';
      ctx.fill();
      ctx.strokeStyle = style.stroke || '#5eead4';
      ctx.lineWidth = (style.lineWidth || 1) / this.scale;
      ctx.stroke();
      if (model.label && this.scale > 0.38) {
        const cfg = model.labelCfg || {};
        const labelStyle = cfg.style || {};
        const below = cfg.position === 'bottom';
        const x = below ? model.x : model.x + radius + 5 / this.scale;
        const y = below ? model.y + radius + 12 / this.scale : model.y;
        ctx.globalAlpha = 1;
        ctx.font = `${labelStyle.fontWeight || 400} ${labelStyle.fontSize || 10}px sans-serif`;
        ctx.textAlign = below ? 'center' : 'left';
        ctx.textBaseline = 'middle';
        ctx.lineWidth = (labelStyle.lineWidth || 0) / this.scale;
        if (labelStyle.stroke && labelStyle.lineWidth) {
          ctx.strokeStyle = labelStyle.stroke;
          ctx.strokeText(model.label, x, y);
        }
        ctx.fillStyle = labelStyle.fill || '#f4f7f8';
        ctx.fillText(model.label, x, y);
      }
      ctx.restore();
    }

    fitView(padding) {
      const visible = this.nodes.filter(node => node.visible);
      if (!visible.length) return;
      const pad = typeof padding === 'number' ? padding : 24;
      const minX = Math.min(...visible.map(n => n.model.x - this.nodeRadius(n)));
      const maxX = Math.max(...visible.map(n => n.model.x + this.nodeRadius(n)));
      const minY = Math.min(...visible.map(n => n.model.y - this.nodeRadius(n)));
      const maxY = Math.max(...visible.map(n => n.model.y + this.nodeRadius(n)));
      const graphWidth = Math.max(1, maxX - minX);
      const graphHeight = Math.max(1, maxY - minY);
      this.scale = Math.min((this.width - pad * 2) / graphWidth, (this.height - pad * 2) / graphHeight, 1.4);
      this.offsetX = this.width / 2 - (minX + maxX) / 2 * this.scale;
      this.offsetY = this.height / 2 - (minY + maxY) / 2 * this.scale;
      this.draw();
    }

    zoom(factor) { this.zoomAt(factor, this.width / 2, this.height / 2); }

    zoomAt(factor, x, y) {
      const before = this.screenToWorld(x, y);
      this.scale = Math.max(0.15, Math.min(4, this.scale * factor));
      this.offsetX = x - before.x * this.scale;
      this.offsetY = y - before.y * this.scale;
      this.draw();
    }

    focusItem(item) {
      const node = this.findById(itemId(item));
      if (!node) return;
      this.offsetX = this.width / 2 - node.model.x * this.scale;
      this.offsetY = this.height / 2 - node.model.y * this.scale;
      this.draw();
    }

    findById(id) { return this.nodeById.get(String(id)) || null; }
    getNodes() { return this.nodes; }
    getEdges() { return this.edges; }

    resolveItem(item) {
      if (typeof item === 'string') return this.findById(item);
      return item;
    }

    setItemState(item, state, enabled) {
      const resolved = this.resolveItem(item);
      if (!resolved) return;
      enabled ? resolved.states.add(state) : resolved.states.delete(state);
      this.draw();
    }

    clearItemStates(item) {
      const resolved = this.resolveItem(item);
      if (!resolved) return;
      resolved.states.clear();
      this.draw();
    }

    showItem(item) {
      const resolved = this.resolveItem(item);
      if (resolved) { resolved.visible = true; this.draw(); }
    }

    hideItem(item) {
      const resolved = this.resolveItem(item);
      if (resolved) { resolved.visible = false; this.draw(); }
    }

    changeSize(width, height) {
      this.width = width;
      this.height = height;
      if (!this.canvas) return;
      this.canvas.width = Math.max(1, Math.round(width * devicePixelRatio));
      this.canvas.height = Math.max(1, Math.round(height * devicePixelRatio));
      this.canvas.style.width = width + 'px';
      this.canvas.style.height = height + 'px';
      this.ctx = this.canvas.getContext('2d');
      this.draw();
    }

    refresh() { this.draw(); }

    destroyCanvas() {
      if (!this.canvas) return;
      if (this.bound) {
        this.canvas.removeEventListener('wheel', this.bound.wheel);
        this.canvas.removeEventListener('pointerdown', this.bound.down);
        this.canvas.removeEventListener('pointermove', this.bound.move);
        this.canvas.removeEventListener('pointerup', this.bound.up);
        this.canvas.removeEventListener('pointerleave', this.bound.leave);
      }
      this.canvas.remove();
      this.canvas = null;
      this.ctx = null;
    }

    destroy() {
      this.destroyed = true;
      this.destroyCanvas();
      this.nodes = [];
      this.edges = [];
      this.nodeById.clear();
    }
  }

  global.G6 = {
    Graph,
    Arrow: {
      triangle: function () { return ''; }
    }
  };
})(window);
