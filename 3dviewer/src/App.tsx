import {useEffect, useRef, useState, useCallback} from 'react';
import * as THREE from 'three';
import {OrbitControls} from 'three/examples/jsm/controls/OrbitControls.js';
import {buildTileTexture} from './geo/tiles';

interface Bounds {
  minX: number; maxX: number;
  minY: number; maxY: number;
  minZ: number; maxZ: number;
}

interface ParseResult {
  positions: ArrayBuffer;
  colors: ArrayBuffer;
  vertexCount: number;
  bounds: Bounds;
  origin: {lon0: number; lat0: number};
}

interface Status {
  text: string;
  loading: boolean;
}

export default function App() {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const rendererRef = useRef<THREE.WebGLRenderer | null>(null);
  const sceneRef = useRef<THREE.Scene | null>(null);
  const cameraRef = useRef<THREE.PerspectiveCamera | null>(null);
  const controlsRef = useRef<OrbitControls | null>(null);
  const groupRef = useRef<THREE.Group | null>(null);
  const tileMeshRef = useRef<THREE.Mesh | null>(null);
  const showTilesRef = useRef(true);
  const animRef = useRef<number>(0);
  const customDragRef = useRef(false); // true while our pick-orbit is active

  const [status, setStatus] = useState<Status>({text: 'Drop a .geojson file or use File → Open', loading: false});
  const [zScale, setZScale] = useState(1);
  const [wireframe, setWireframe] = useState(false);
  const [showTiles, setShowTiles] = useState(true);
  const [fileName, setFileName] = useState('');

  // Initialize Three.js scene
  useEffect(() => {
    const canvas = canvasRef.current!;
    const renderer = new THREE.WebGLRenderer({canvas, antialias: true});
    renderer.setPixelRatio(window.devicePixelRatio);
    renderer.setSize(canvas.clientWidth, canvas.clientHeight, false);
    rendererRef.current = renderer;

    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0x111122);
    sceneRef.current = scene;

    const camera = new THREE.PerspectiveCamera(55, canvas.clientWidth / canvas.clientHeight, 100, 50_000_000);
    camera.position.set(0, -2_000_000, 1_000_000);
    camera.up.set(0, 0, 1);
    cameraRef.current = camera;

    const controls = new OrbitControls(camera, canvas);
    controls.enableDamping = false;
    controls.screenSpacePanning = true;
    controls.minDistance = 1000;
    controls.maxDistance = 20_000_000;
    controlsRef.current = controls;

    // Grid at z=0 (surface level) for orientation
    const grid = new THREE.GridHelper(2_000_000, 20, 0x334455, 0x223344);
    grid.rotation.x = Math.PI / 2;
    scene.add(grid);

    const group = new THREE.Group();
    scene.add(group);
    groupRef.current = group;

    const animate = () => {
      animRef.current = requestAnimationFrame(animate);
      // Skip controls.update() during our custom drag: OrbitControls.update() always
      // calls camera.lookAt(target) which would override our pick-orbit rotation.
      if (!customDragRef.current) controls.update();
      renderer.render(scene, camera);
    };
    animate();

    const onResize = () => {
      const w = canvas.clientWidth, h = canvas.clientHeight;
      renderer.setSize(w, h, false);
      camera.aspect = w / h;
      camera.updateProjectionMatrix();
    };
    window.addEventListener('resize', onResize);

    return () => {
      cancelAnimationFrame(animRef.current);
      window.removeEventListener('resize', onResize);
      controls.dispose();
      renderer.dispose();
    };
  }, []);

  // z-scale slider updates group scale
  useEffect(() => {
    if (groupRef.current) groupRef.current.scale.z = zScale;
  }, [zScale]);

  // Tile visibility toggle
  useEffect(() => {
    showTilesRef.current = showTiles;
    if (tileMeshRef.current) tileMeshRef.current.visible = showTiles;
  }, [showTiles]);

  // Pick-to-orbit: orbit around the picked surface point with no translation.
  // Strategy: on each pointermove we recompute the full rotation R from the
  // INITIAL camera state (snapshot at pointerdown), not incrementally.
  // Because C' = p + R*(C0-p) and Q' = R*Q0 use the same R, the pick point's
  // camera-space coordinates are mathematically constant: Q'^-1*(p-C') = Q0^-1*(p-C0).
  useEffect(() => {
    const canvas = canvasRef.current!;
    const raycaster = new THREE.Raycaster();
    const ndc = new THREE.Vector2();
    const pickPoint = new THREE.Vector3();
    const initCamPos = new THREE.Vector3();
    const initCamQuat = new THREE.Quaternion();
    const initRight = new THREE.Vector3();
    const startMouse = new THREE.Vector2();
    const worldUp = new THREE.Vector3(0, 0, 1);
    let dragging = false;

    const onPointerDown = (e: PointerEvent) => {
      if (e.button !== 0) return;
      const camera = cameraRef.current!;
      const rect = canvas.getBoundingClientRect();
      ndc.x = ((e.clientX - rect.left) / rect.width) * 2 - 1;
      ndc.y = -((e.clientY - rect.top) / rect.height) * 2 + 1;
      raycaster.setFromCamera(ndc, camera);
      const hits = raycaster.intersectObjects(groupRef.current!.children, true);
      if (hits.length === 0) return;

      pickPoint.copy(hits[0].point);
      initCamPos.copy(camera.position);
      initCamQuat.copy(camera.quaternion);
      // Camera's right axis at drag start (local +X in world space)
      initRight.set(1, 0, 0).applyQuaternion(initCamQuat);
      startMouse.set(e.clientX, e.clientY);
      dragging = true;
      customDragRef.current = true;
      // Stop propagation so OrbitControls never sees this pointerdown and
      // never starts its own drag (which would also call update() internally).
      e.stopPropagation();
    };

    const onPointerMove = (e: PointerEvent) => {
      if (!dragging) return;
      const camera = cameraRef.current!;

      const totalDx = (e.clientX - startMouse.x) / canvas.clientWidth;
      const totalDy = (e.clientY - startMouse.y) / canvas.clientHeight;

      // Yaw around world Z, pitch around initial camera right — applied from initial state
      const yawQ = new THREE.Quaternion().setFromAxisAngle(worldUp, -totalDx * Math.PI);
      // Pitch axis is initRight after the current yaw, for natural feel
      const yawedRight = initRight.clone().applyQuaternion(yawQ);
      const pitchQ = new THREE.Quaternion().setFromAxisAngle(yawedRight, -totalDy * Math.PI);
      const R = pitchQ.multiply(yawQ); // pitchQ = pitchQ*yawQ (yaw applied first)

      // Camera position: rotate initial offset around pick point
      camera.position
        .copy(initCamPos)
        .sub(pickPoint)
        .applyQuaternion(R)
        .add(pickPoint);

      // Camera orientation: same rotation applied to initial orientation
      camera.quaternion.copy(initCamQuat).premultiply(R);
    };

    const onPointerUp = (e: PointerEvent) => {
      if (e.button !== 0 || !dragging) return;
      dragging = false;
      const camera = cameraRef.current!;
      const controls = controlsRef.current!;
      // Aim controls target along camera look direction at pick-point distance,
      // so OrbitControls re-enable doesn't snap the camera orientation.
      const dist = camera.position.distanceTo(pickPoint);
      const lookDir = camera.getWorldDirection(new THREE.Vector3());
      controls.target.copy(camera.position).addScaledVector(lookDir, dist);
      customDragRef.current = false;
    };

    const onPointerCancel = () => {
      if (!dragging) return;
      dragging = false;
      customDragRef.current = false;
    };

    canvas.addEventListener('pointerdown', onPointerDown, {capture: true});
    window.addEventListener('pointermove', onPointerMove);
    window.addEventListener('pointerup', onPointerUp);
    window.addEventListener('pointercancel', onPointerCancel);
    return () => {
      canvas.removeEventListener('pointerdown', onPointerDown, {capture: true});
      window.removeEventListener('pointermove', onPointerMove);
      window.removeEventListener('pointerup', onPointerUp);
      window.removeEventListener('pointercancel', onPointerCancel);
    };
  }, []);

  // Wireframe toggle
  useEffect(() => {
    if (!groupRef.current) return;
    groupRef.current.traverse((obj: THREE.Object3D) => {
      if (obj instanceof THREE.Mesh) {
        (obj.material as THREE.MeshBasicMaterial).wireframe = wireframe;
      }
    });
  }, [wireframe]);

  const loadFile = useCallback(async (path: string) => {
    setStatus({text: 'Reading…', loading: true});
    setFileName(path.split('/').pop() ?? path);

    const t0 = performance.now();
    let buffer: Uint8Array;
    try {
      buffer = await window.electronAPI.readFile(path);
    } catch (err) {
      setStatus({text: `Read error: ${String(err)}`, loading: false});
      return;
    }

    setStatus({text: 'Parsing…', loading: true});
    const worker = new Worker(new URL('./geo/parseWorker.ts', import.meta.url), {type: 'module'});

    worker.onmessage = (e: MessageEvent<ParseResult & {error?: string}>) => {
      worker.terminate();
      if (e.data.error) {
        setStatus({text: `Error: ${e.data.error}`, loading: false});
        return;
      }

      const {positions, colors, vertexCount, bounds, origin} = e.data;

      const posArr = new Float32Array(positions);
      const colArr = new Float32Array(colors);

      const geo = new THREE.BufferGeometry();
      geo.setAttribute('position', new THREE.BufferAttribute(posArr, 3));
      geo.setAttribute('color', new THREE.BufferAttribute(colArr, 3));
      geo.computeBoundingBox();

      const mat = new THREE.MeshBasicMaterial({
        vertexColors: true,
        side: THREE.DoubleSide,
        wireframe: wireframe,
      });
      const mesh = new THREE.Mesh(geo, mat);

      const group = groupRef.current!;
      // Remove previous mesh
      while (group.children.length > 0) {
        const child = group.children[0] as THREE.Mesh;
        child.geometry.dispose();
        (child.material as THREE.Material).dispose();
        group.remove(child);
      }
      group.add(mesh);
      group.scale.z = zScale;

      // Position camera to see all data
      const cx = (bounds.minX + bounds.maxX) / 2;
      const cy = (bounds.minY + bounds.maxY) / 2;
      const spanX = bounds.maxX - bounds.minX;
      const spanY = bounds.maxY - bounds.minY;
      const span = Math.max(spanX, spanY);

      const camera = cameraRef.current!;
      const controls = controlsRef.current!;
      controls.target.set(cx, cy, 0);
      camera.position.set(cx, cy - span * 0.8, span * 0.5);
      camera.up.set(0, 0, 1);
      controls.update();

      const elapsed = ((performance.now() - t0) / 1000).toFixed(1);
      const triCount = (vertexCount / 3).toLocaleString();
      setStatus({text: `${triCount} triangles — loaded in ${elapsed}s`, loading: false});

      // Remove previous tile mesh
      const scene = sceneRef.current!;
      if (tileMeshRef.current) {
        (tileMeshRef.current.material as THREE.Material).dispose();
        tileMeshRef.current.geometry.dispose();
        scene.remove(tileMeshRef.current);
        tileMeshRef.current = null;
      }

      // Fetch and place tile layer asynchronously
      buildTileTexture(origin, bounds).then(({canvas, geoWest, geoEast, geoNorth, geoSouth}) => {
        const cosLat = Math.cos(origin.lat0 * Math.PI / 180);
        const enuMinX = (geoWest  - origin.lon0) * 111320 * cosLat;
        const enuMaxX = (geoEast  - origin.lon0) * 111320 * cosLat;
        const enuMinY = (geoSouth - origin.lat0) * 110540;
        const enuMaxY = (geoNorth - origin.lat0) * 110540;

        const width  = enuMaxX - enuMinX;
        const height = enuMaxY - enuMinY;
        const cx = (enuMinX + enuMaxX) / 2;
        const cy = (enuMinY + enuMaxY) / 2;

        const texture = new THREE.CanvasTexture(canvas);
        const mat = new THREE.MeshBasicMaterial({
          map: texture,
          transparent: true,
          opacity: 0.5,
          depthWrite: false,
          side: THREE.DoubleSide,
        });
        const geo = new THREE.PlaneGeometry(width, height);
        const mesh = new THREE.Mesh(geo, mat);
        mesh.position.set(cx, cy, 1); // 1 m above surface to avoid z-fighting
        mesh.visible = showTilesRef.current;
        scene.add(mesh);
        tileMeshRef.current = mesh;
      }).catch(() => { /* tile fetch failed silently */ });

    };

    worker.onerror = (err) => {
      worker.terminate();
      setStatus({text: `Worker error: ${err.message}`, loading: false});
    };

    // Transfer the buffer to the worker (zero-copy)
    worker.postMessage({type: 'parse', buffer: buffer.buffer}, [buffer.buffer]);
  }, [wireframe, zScale]);

  // Listen for file paths from Electron menu
  useEffect(() => {
    window.electronAPI.onOpenFilePath((path) => loadFile(path));
  }, [loadFile]);

  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    const file = e.dataTransfer.files[0];
    if (!file) return;
    const path = window.electronAPI.getPathForFile(file);
    if (path) loadFile(path);
  }, [loadFile]);

  const onDragOver = (e: React.DragEvent) => e.preventDefault();

  return (
    <div style={{width: '100vw', height: '100vh', position: 'relative'}} onDrop={onDrop} onDragOver={onDragOver}>
      <canvas
        ref={canvasRef}
        style={{width: '100%', height: '100%', display: 'block'}}
      />
      <div style={toolbarStyle}>
        {fileName && <span style={{opacity: 0.6, maxWidth: 300, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap'}}>{fileName}</span>}
        <label style={{display: 'flex', alignItems: 'center', gap: 6}}>
          Z×{zScale}
          <input
            type="range" min={1} max={50} step={1} value={zScale}
            onChange={e => setZScale(Number(e.target.value))}
            style={{width: 90}}
          />
        </label>
        <label style={{display: 'flex', alignItems: 'center', gap: 4, cursor: 'pointer'}}>
          <input type="checkbox" checked={wireframe} onChange={e => setWireframe(e.target.checked)} />
          Wireframe
        </label>
        <label style={{display: 'flex', alignItems: 'center', gap: 4, cursor: 'pointer'}}>
          <input type="checkbox" checked={showTiles} onChange={e => setShowTiles(e.target.checked)} />
          Tiles
        </label>
        <span style={{marginLeft: 'auto', opacity: status.loading ? 1 : 0.7}}>
          {status.loading && <span style={{marginRight: 6}}>⏳</span>}
          {status.text}
        </span>
      </div>
    </div>
  );
}

const toolbarStyle: React.CSSProperties = {
  position: 'absolute',
  top: 0,
  left: 0,
  right: 0,
  padding: '6px 14px',
  background: 'rgba(0,0,0,0.65)',
  color: '#ccc',
  display: 'flex',
  alignItems: 'center',
  gap: 16,
  fontFamily: 'monospace',
  fontSize: 12,
  backdropFilter: 'blur(4px)',
  userSelect: 'none',
};
