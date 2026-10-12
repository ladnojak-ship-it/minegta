// Presenter half of MineGTA: a D3D11 hook that puts Minecraft's picture where GTA's own picture would
// be. No ReShade, no depth compositing, no extra shader pack in the game folder: GTA still renders its
// world, and every frame we overwrite the back buffer with Minecraft's frame (world colour, then the
// hand and HUD overlay on top with premultiplied alpha).
#include "present.h"

#include <windows.h>
#include <d3d11.h>
#include <d3dcompiler.h>
#include <dxgi.h>

#include <cstdarg>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <vector>

#pragma comment(lib, "d3d11.lib")
#pragma comment(lib, "d3dcompiler.lib")

namespace
{
	// Shared memory "Local\MCPassthroughFrame", written by the Fabric mod's FrameExporter.
	constexpr wchar_t kShmName[] = L"Local\\MCPassthroughFrame";
	constexpr uint32_t kMagic = 0x5450434Du;
	constexpr int kHeader = 4096;
	constexpr int kSlots = 3;
	constexpr int kSlotDescBytes = 128;

	// Offsets inside a 128-byte slot descriptor.
	constexpr int kOffSeq = 0;
	constexpr int kOffWidth = 24;
	constexpr int kOffHeight = 28;
	constexpr int kOffFlags = 44;
	// Bit 1 of flags: the rows are bottom-up (OpenGL's order), which is how Minecraft hands them over.
	constexpr uint32_t kFlagRowsBottomUp = 2u;

	// Offsets in the 4096-byte header.
	constexpr int kOffStride = 16;
	constexpr int kOffPublish = 32;
	constexpr int kOffLatest = 40;

	using PresentFn = HRESULT(STDMETHODCALLTYPE *)(IDXGISwapChain *, UINT, UINT);
	using Present1Fn = HRESULT(STDMETHODCALLTYPE *)(IDXGISwapChain1 *, UINT, UINT, const DXGI_PRESENT_PARAMETERS *);

	PresentFn g_realPresent = nullptr;
	Present1Fn g_realPresent1 = nullptr;
	void **g_vtable = nullptr;
	void **g_vtable1 = nullptr;
	bool g_unloading = false;

	bool g_active = true;
	bool g_installed = false;
	bool g_broken = false;
	int g_backW = 0;
	int g_backH = 0;
	uint64_t g_drawn = 0;
	uint64_t g_uploads = 0;
	uint64_t g_pulls = 0;
	uint64_t g_lastPublish = 0;

	HANDLE g_map = nullptr;
	const uint8_t *g_shm = nullptr;
	uint64_t g_lastMapTry = 0;

	// The newest frame we have seen, copied out of shared memory so it survives the slot ring.
	struct Frame
	{
		uint32_t width = 0;
		uint32_t height = 0;
		bool flip = false;
		std::vector<uint8_t> colour;
		std::vector<uint8_t> overlay;
		bool valid = false;
	};

	Frame g_frame;

	ID3D11Device *g_device = nullptr;
	ID3D11DeviceContext *g_context = nullptr;
	ID3D11VertexShader *g_vertexShader = nullptr;
	ID3D11PixelShader *g_plainShader = nullptr;
	ID3D11PixelShader *g_flipShader = nullptr;
	ID3D11SamplerState *g_sampler = nullptr;
	ID3D11RasterizerState *g_rasterizer = nullptr;
	ID3D11DepthStencilState *g_depthOff = nullptr;
	ID3D11BlendState *g_overlayBlend = nullptr;
	ID3D11Texture2D *g_textures[2] = {nullptr, nullptr};
	ID3D11ShaderResourceView *g_shaderViews[2] = {nullptr, nullptr};
	uint32_t g_textureW = 0;
	uint32_t g_textureH = 0;

	constexpr char kShaders[] = R"(
Texture2D<float4> g_frameTex : register(t0);
SamplerState g_frameSampler : register(s0);
struct VSOut { float4 position : SV_POSITION; float2 uv : TEXCOORD0; };
VSOut vs_main(uint vertexId : SV_VertexID) {
	VSOut output;
	float x = (vertexId == 1u) ? 3.0 : -1.0;
	float y = (vertexId == 2u) ? 3.0 : -1.0;
	output.position = float4(x, y, 0.0, 1.0);
	output.uv = float2((x + 1.0) * 0.5, (y + 1.0) * 0.5);
	return output;
}
float4 ps_plain(VSOut input) : SV_TARGET { return g_frameTex.Sample(g_frameSampler, input.uv); }
float4 ps_flipped(VSOut input) : SV_TARGET { return g_frameTex.Sample(g_frameSampler, float2(input.uv.x, 1.0 - input.uv.y)); }
)";

	uint32_t read32(const uint8_t *at)
	{
		uint32_t value;
		std::memcpy(&value, at, sizeof(value));
		return value;
	}

	uint64_t read64(const uint8_t *at)
	{
		uint64_t value;
		std::memcpy(&value, at, sizeof(value));
		return value;
	}

	DXGI_FORMAT stripSrgb(DXGI_FORMAT format)
	{
		switch (format)
		{
			case DXGI_FORMAT_R8G8B8A8_UNORM_SRGB: return DXGI_FORMAT_R8G8B8A8_UNORM;
			case DXGI_FORMAT_B8G8R8A8_UNORM_SRGB: return DXGI_FORMAT_B8G8R8A8_UNORM;
			case DXGI_FORMAT_B8G8R8X8_UNORM_SRGB: return DXGI_FORMAT_B8G8R8X8_UNORM;
			default: return format;
		}
	}

	void logLine(const char *text)
	{
		char path[MAX_PATH] = {};
		GetModuleFileNameA(nullptr, path, MAX_PATH);
		char *slash = std::strrchr(path, '\\');
		if (slash)
		{
			*(slash + 1) = '\0';
		}
		std::strcat(path, "MCOnly.log");
		FILE *file = std::fopen(path, "a");
		if (file)
		{
			std::fprintf(file, "%s\n", text);
			std::fclose(file);
		}
	}

	void logFormat(const char *format, ...)
	{
		char buffer[512];
		va_list args;
		va_start(args, format);
		std::vsnprintf(buffer, sizeof(buffer), format, args);
		va_end(args);
		logLine(buffer);
	}

	// ------------------------------------------------------------ d3d side

	bool ensureStates();
	bool ensureDevice(IDXGISwapChain *chain);
	void releaseTextures();
	bool pullFrame();
	bool drawFrame(IDXGISwapChain *chain);

	bool ensureStates()
	{
		if (g_sampler)
		{
			return true;
		}

		ID3DBlob *vertexBlob = nullptr;
		ID3DBlob *plainBlob = nullptr;
		ID3DBlob *flipBlob = nullptr;
		ID3DBlob *errors = nullptr;

		auto compile = [&errors](const char *entry, const char *target, ID3DBlob **out) {
			return D3DCompile(kShaders, sizeof(kShaders) - 1, "MCOnly", nullptr, nullptr, entry, target, D3DCOMPILE_OPTIMIZATION_LEVEL2, 0, out, &errors) == S_OK;
		};

		if (!compile("vs_main", "vs_5_0", &vertexBlob)
			|| !compile("ps_plain", "ps_5_0", &plainBlob)
			|| !compile("ps_flipped", "ps_5_0", &flipBlob))
		{
			logLine("MCOnly: shader compile failed");
			if (errors)
			{
				logLine(static_cast<const char *>(errors->GetBufferPointer()));
			}

			return false;
		}

		if (FAILED(g_device->CreateVertexShader(vertexBlob->GetBufferPointer(), vertexBlob->GetBufferSize(), nullptr, &g_vertexShader))
			|| FAILED(g_device->CreatePixelShader(plainBlob->GetBufferPointer(), plainBlob->GetBufferSize(), nullptr, &g_plainShader))
			|| FAILED(g_device->CreatePixelShader(flipBlob->GetBufferPointer(), flipBlob->GetBufferSize(), nullptr, &g_flipShader)))
		{
			logLine("MCOnly: shader creation failed");
			return false;
		}

		D3D11_SAMPLER_DESC samplerDesc = {};
		samplerDesc.Filter = D3D11_FILTER_MIN_MAG_MIP_LINEAR;
		samplerDesc.AddressU = D3D11_TEXTURE_ADDRESS_CLAMP;
		samplerDesc.AddressV = D3D11_TEXTURE_ADDRESS_CLAMP;
		samplerDesc.MaxLOD = D3D11_FLOAT32_MAX;
		if (FAILED(g_device->CreateSamplerState(&samplerDesc, &g_sampler)))
		{
			return false;
		}

		D3D11_RASTERIZER_DESC rasterDesc = {};
		rasterDesc.FillMode = D3D11_FILL_SOLID;
		rasterDesc.CullMode = D3D11_CULL_NONE;
		rasterDesc.DepthClipEnable = TRUE;
		if (FAILED(g_device->CreateRasterizerState(&rasterDesc, &g_rasterizer)))
		{
			return false;
		}

		D3D11_DEPTH_STENCIL_DESC depthDesc = {};
		depthDesc.DepthEnable = FALSE;
		depthDesc.DepthWriteMask = D3D11_DEPTH_WRITE_MASK_ZERO;
		depthDesc.StencilEnable = FALSE;
		if (FAILED(g_device->CreateDepthStencilState(&depthDesc, &g_depthOff)))
		{
			return false;
		}

		// Premultiplied alpha, which is what the mod's overlay layer carries.
		D3D11_BLEND_DESC blendDesc = {};
		blendDesc.RenderTarget[0].BlendEnable = TRUE;
		blendDesc.RenderTarget[0].SrcBlend = D3D11_BLEND_ONE;
		blendDesc.RenderTarget[0].DestBlend = D3D11_BLEND_INV_SRC_ALPHA;
		blendDesc.RenderTarget[0].BlendOp = D3D11_BLEND_OP_ADD;
		blendDesc.RenderTarget[0].SrcBlendAlpha = D3D11_BLEND_ONE;
		blendDesc.RenderTarget[0].DestBlendAlpha = D3D11_BLEND_INV_SRC_ALPHA;
		blendDesc.RenderTarget[0].BlendOpAlpha = D3D11_BLEND_OP_ADD;
		blendDesc.RenderTarget[0].RenderTargetWriteMask = D3D11_COLOR_WRITE_ENABLE_ALL;
		if (FAILED(g_device->CreateBlendState(&blendDesc, &g_overlayBlend)))
		{
			return false;
		}

		vertexBlob->Release();
		plainBlob->Release();
		flipBlob->Release();
		if (errors)
		{
			errors->Release();
		}

		logLine("MCOnly: shaders and states ready");
		return true;
	}

	bool ensureDevice(IDXGISwapChain *chain)
	{
		if (g_device)
		{
			return true;
		}

		ID3D11Device *device = nullptr;
		if (FAILED(chain->GetDevice(__uuidof(ID3D11Device), reinterpret_cast<void **>(&device))) || !device)
		{
			return false;
		}

		if (FAILED(device->GetImmediateContext(&g_context)))
		{
			device->Release();
			return false;
		}

		g_device = device;
		return ensureStates();
	}

	void releaseTextures()
	{
		ID3D11ShaderResourceView *nullView[2] = {nullptr, nullptr};
		if (g_context)
		{
			g_context->PSSetShaderResources(0, 2, nullView);
		}

		for (int layer = 0; layer < 2; layer++)
		{
			if (g_shaderViews[layer])
			{
				g_shaderViews[layer]->Release();
				g_shaderViews[layer] = nullptr;
			}

			if (g_textures[layer])
			{
				g_textures[layer]->Release();
				g_textures[layer] = nullptr;
			}
		}

		g_textureW = 0;
		g_textureH = 0;
	}

	bool uploadFrame()
	{
		if (!g_frame.valid || g_frame.width == 0 || g_frame.height == 0)
		{
			return false;
		}

		if (g_textureW != g_frame.width || g_textureH != g_frame.height)
		{
			releaseTextures();

			D3D11_TEXTURE2D_DESC desc = {};
			desc.Width = g_frame.width;
			desc.Height = g_frame.height;
			desc.MipLevels = 1;
			desc.ArraySize = 1;
			desc.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
			desc.SampleDesc.Count = 1;
			desc.Usage = D3D11_USAGE_DEFAULT;
			desc.BindFlags = D3D11_BIND_SHADER_RESOURCE;

			for (int layer = 0; layer < 2; layer++)
			{
				if (FAILED(g_device->CreateTexture2D(&desc, nullptr, &g_textures[layer])))
				{
					logLine("MCOnly: could not create the frame texture");
					return false;
				}

				D3D11_SHADER_RESOURCE_VIEW_DESC viewDesc = {};
				viewDesc.Format = desc.Format;
				viewDesc.ViewDimension = D3D11_SRV_DIMENSION_TEXTURE2D;
				viewDesc.Texture2D.MipLevels = 1;
				if (FAILED(g_device->CreateShaderResourceView(g_textures[layer], &viewDesc, &g_shaderViews[layer])))
				{
					logLine("MCOnly: could not create the frame view");
					return false;
				}
			}

			g_textureW = g_frame.width;
			g_textureH = g_frame.height;
			logFormat("MCOnly: frame textures %ux%u", g_frame.width, g_frame.height);
		}

		const size_t bytes = size_t(g_frame.width) * g_frame.height * 4u;
		if (g_frame.colour.size() >= bytes)
		{
			g_context->UpdateSubresource(g_textures[0], 0, nullptr, g_frame.colour.data(), UINT(g_frame.width * 4u), UINT(bytes));
		}

		if (g_frame.overlay.size() >= bytes)
		{
			g_context->UpdateSubresource(g_textures[1], 0, nullptr, g_frame.overlay.data(), UINT(g_frame.width * 4u), UINT(bytes));
		}

		g_uploads++;
		return true;
	}

	// ------------------------------------------------------------ shared memory

	bool openSharedMemory()
	{
		if (g_shm)
		{
			return true;
		}

		g_map = OpenFileMappingW(FILE_MAP_ALL_ACCESS, FALSE, kShmName);
		if (!g_map)
		{
			return false;
		}

		g_shm = static_cast<const uint8_t *>(MapViewOfFile(g_map, FILE_MAP_ALL_ACCESS, 0, 0, 0));
		if (!g_shm)
		{
			CloseHandle(g_map);
			g_map = nullptr;
			return false;
		}

		if (read32(g_shm) != kMagic)
		{
			logLine("MCOnly: shared memory is not a MineGTA frame buffer");
			UnmapViewOfFile(g_shm);
			g_shm = nullptr;
			CloseHandle(g_map);
			g_map = nullptr;
			return false;
		}

		logLine("MCOnly: connected to the Minecraft frame buffer");
		return true;
	}

	/** Copy the newest finished slot out of shared memory, once per published frame. */
	bool pullFrame()
	{
		if (!openSharedMemory())
		{
			return false;
		}

		const uint64_t publish = read64(g_shm + kOffPublish);
		if (publish == 0 || publish == g_lastPublish)
		{
			return g_frame.valid;
		}

		int best = -1;
		uint64_t bestSeq = 0;

		for (int slot = 0; slot < kSlots; slot++)
		{
			const uint8_t *desc = g_shm + 256 + int64_t(kSlotDescBytes) * slot;
			const uint64_t seq = read64(desc + kOffSeq);
			if ((seq & 1u) != 0u)
			{
				continue; // being written right now
			}

			if (seq != 0 && seq >= bestSeq)
			{
				bestSeq = seq;
				best = slot;
			}
		}

		if (best < 0)
		{
			return g_frame.valid;
		}

		const uint8_t *desc = g_shm + 256 + int64_t(kSlotDescBytes) * best;
		const uint32_t width = read32(desc + kOffWidth);
		const uint32_t height = read32(desc + kOffHeight);
		const uint32_t flags = read32(desc + kOffFlags);

		if (width == 0 || height == 0 || width > 7680 || height > 4320)
		{
			return g_frame.valid;
		}

		const uint64_t stride = read64(g_shm + kOffStride);
		const size_t bytes = size_t(width) * height * 4u;
		if (stride < bytes * 3u)
		{
			return g_frame.valid;
		}

		const uint8_t *data = g_shm + kHeader + uint64_t(stride) * best;

		if (g_frame.width != width || g_frame.height != height || g_frame.colour.size() != bytes)
		{
			g_frame.width = width;
			g_frame.height = height;
			g_frame.colour.assign(bytes, 0);
			g_frame.overlay.assign(bytes, 0);
		}

		std::memcpy(g_frame.colour.data(), data, bytes);
		std::memcpy(g_frame.overlay.data(), data + bytes * 2u, bytes);
		g_frame.flip = (flags & kFlagRowsBottomUp) == 0u;
		g_frame.valid = true;
		g_lastPublish = publish;
		g_pulls++;
		return true;
	}

	// ------------------------------------------------------------ drawing

	ID3D11RenderTargetView *targetFor(IDXGISwapChain *chain, int &width, int &height)
	{
		ID3D11Texture2D *back = nullptr;
		if (FAILED(chain->GetBuffer(0, __uuidof(ID3D11Texture2D), reinterpret_cast<void **>(&back))) || !back)
		{
			return nullptr;
		}

		DXGI_SWAP_CHAIN_DESC desc = {};
		chain->GetDesc(&desc);

		D3D11_RENDER_TARGET_VIEW_DESC viewDesc = {};
		viewDesc.Format = stripSrgb(desc.BufferDesc.Format);
		viewDesc.ViewDimension = desc.SampleDesc.Count > 1 ? D3D11_RTV_DIMENSION_TEXTURE2DMS : D3D11_RTV_DIMENSION_TEXTURE2D;

		ID3D11RenderTargetView *target = nullptr;
		if (FAILED(g_device->CreateRenderTargetView(back, &viewDesc, &target)))
		{
			back->Release();
			return nullptr;
		}

		back->Release();
		width = int(desc.BufferDesc.Width);
		height = int(desc.BufferDesc.Height);
		return target;
	}

	void drawQuad(ID3D11PixelShader *pixel)
	{
		if (!pixel)
		{
			return;
		}

		g_context->IASetInputLayout(nullptr);
		g_context->IASetPrimitiveTopology(D3D11_PRIMITIVE_TOPOLOGY_TRIANGLELIST);
		g_context->VSSetShader(g_vertexShader, nullptr, 0);
		g_context->PSSetShader(pixel, nullptr, 0);
		g_context->PSSetSamplers(0, 1, &g_sampler);
		g_context->RSSetState(g_rasterizer);
		g_context->OMSetDepthStencilState(g_depthOff, 0);
		g_context->Draw(3, 0);

		ID3D11ShaderResourceView *unbind[1] = {nullptr};
		g_context->PSSetShaderResources(0, 1, unbind);
	}

	bool drawFrame(IDXGISwapChain *chain)
	{
		if (!ensureDevice(chain))
		{
			return false;
		}

		pullFrame();

		int width = 0;
		int height = 0;
		ID3D11RenderTargetView *target = targetFor(chain, width, height);
		if (!target)
		{
			return false;
		}

		g_backW = width;
		g_backH = height;

		ID3D11RenderTargetView *targets[1] = {target};
		g_context->OMSetRenderTargets(1, targets, nullptr);

		D3D11_VIEWPORT viewport = {};
		viewport.Width = float(width);
		viewport.Height = float(height);
		viewport.MaxDepth = 1.0f;
		g_context->RSSetViewports(1, &viewport);

		if (!uploadFrame())
		{
			// Minecraft has not shown up yet: fill with a dark slate so it does not look like a crash.
			const float slate[4] = {0.055f, 0.078f, 0.101f, 1.0f};
			g_context->ClearRenderTargetView(target, slate);
			g_context->Flush();

			target->Release();
			g_drawn++;
			return true;
		}

		const float blendFactor[4] = {0.0f, 0.0f, 0.0f, 0.0f};
		ID3D11PixelShader *pixel = g_frame.flip ? g_flipShader : g_plainShader;

		ID3D11ShaderResourceView *views[2] = {g_shaderViews[0], g_shaderViews[1]};
		g_context->PSSetShaderResources(0, 2, views);

		g_context->OMSetBlendState(nullptr, blendFactor, 0xFFFFFFFFu);
		drawQuad(pixel);

		g_context->OMSetBlendState(g_overlayBlend, blendFactor, 0xFFFFFFFFu);
		drawQuad(pixel);
		g_context->OMSetBlendState(nullptr, blendFactor, 0xFFFFFFFFu);

		target->Release();
		g_drawn++;
		return true;
	}

	// ------------------------------------------------------------ hook

	HRESULT STDMETHODCALLTYPE hookedPresent(IDXGISwapChain *chain, UINT syncInterval, UINT flags)
	{
		if (g_active && !g_broken && chain)
		{
			drawFrame(chain);
		}

		return g_realPresent ? g_realPresent(chain, syncInterval, flags) : S_OK;
	}

	HRESULT STDMETHODCALLTYPE hookedPresent1(IDXGISwapChain1 *chain, UINT syncInterval, UINT flags, const DXGI_PRESENT_PARAMETERS *parameters)
	{
		if (g_active && !g_broken && chain)
		{
			drawFrame(chain);
		}

		return g_realPresent1 ? g_realPresent1(chain, syncInterval, flags, parameters) : S_OK;
	}

	bool installHook()
	{
		if (g_installed)
		{
			return true;
		}

		if (g_broken)
		{
			return false;
		}

		WNDCLASSEXW windowClass = {};
		windowClass.cbSize = sizeof(windowClass);
		windowClass.lpfnWndProc = DefWindowProcW;
		windowClass.hInstance = GetModuleHandleW(nullptr);
		windowClass.lpszClassName = L"MCOnlyDummy";
		RegisterClassExW(&windowClass);
		HWND window = CreateWindowExW(0, windowClass.lpszClassName, L"MCOnly", WS_OVERLAPPEDWINDOW, 0, 0, 64, 64, nullptr, nullptr, windowClass.hInstance, nullptr);
		if (!window)
		{
			logLine("MCOnly: could not open a dummy window");
			return false;
		}

		DXGI_SWAP_CHAIN_DESC desc = {};
		desc.BufferCount = 2;
		desc.BufferDesc.Width = 8;
		desc.BufferDesc.Height = 8;
		desc.BufferDesc.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
		desc.BufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
		desc.OutputWindow = window;
		desc.SampleDesc.Count = 1;
		desc.Windowed = TRUE;
		desc.SwapEffect = DXGI_SWAP_EFFECT_DISCARD;

		ID3D11Device *device = nullptr;
		IDXGISwapChain *swap = nullptr;
		IDXGISwapChain1 *swap1 = nullptr;
		ID3D11DeviceContext *context = nullptr;
		D3D_FEATURE_LEVEL level = D3D_FEATURE_LEVEL_11_0;

		HRESULT result = D3D11CreateDeviceAndSwapChain(
			nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr, 0, nullptr, 0, D3D11_SDK_VERSION,
			&desc, &swap, &device, &level, &context);

		DestroyWindow(window);
		UnregisterClassW(windowClass.lpszClassName, windowClass.hInstance);

		if (FAILED(result) || !swap)
		{
			logLine("MCOnly: could not create a device and swapchain to hook");
			return false;
		}

		void **table = *reinterpret_cast<void ***>(swap);
		if (!table)
		{
			device->Release();
			context->Release();
			swap->Release();
			return false;
		}

		// Present is vtable index 8. The vtable belongs to the class, so patching it covers GTA's own
		// swapchain even though that one was (or will be) created by the game.
		DWORD protection = 0;
		if (!VirtualProtect(&table[8], sizeof(void *), PAGE_READWRITE, &protection) || !table[8])
		{
			logLine("MCOnly: could not make the vtable writable");
			device->Release();
			context->Release();
			swap->Release();
			return false;
		}

		g_realPresent = reinterpret_cast<PresentFn>(table[8]);
		table[8] = reinterpret_cast<void *>(&hookedPresent);
		g_vtable = table;

		// Present1 (vtable index 22 on IDXGISwapChain1) for swapchains that use it.
		if (swap->QueryInterface(__uuidof(IDXGISwapChain1), reinterpret_cast<void **>(&swap1)) == S_OK && swap1)
		{
			void **table1 = *reinterpret_cast<void ***>(swap1);
			DWORD protect1 = 0;
			if (table1 && table1[22] && VirtualProtect(&table1[22], sizeof(void *), PAGE_READWRITE, &protect1))
			{
				g_realPresent1 = reinterpret_cast<Present1Fn>(table1[22]);
				table1[22] = reinterpret_cast<void *>(&hookedPresent1);
				g_vtable1 = table1;
				VirtualProtect(&table1[22], sizeof(void *), protect1, &protect1);
			}

			swap1->Release();
		}

		VirtualProtect(&table[8], sizeof(void *), protection, &protection);

		device->Release();
		context->Release();
		swap->Release();

		g_installed = true;
		logLine("MCOnly: Present hook installed, Minecraft's picture replaces GTA's");
		return true;
	}

	void removeHook()
	{
		if (!g_installed)
		{
			return;
		}

		if (g_vtable && g_realPresent)
		{
			DWORD protection = 0;
			if (VirtualProtect(&g_vtable[8], sizeof(void *), PAGE_READWRITE, &protection))
			{
				g_vtable[8] = reinterpret_cast<void *>(g_realPresent);
				VirtualProtect(&g_vtable[8], sizeof(void *), protection, &protection);
			}
		}

		if (g_vtable1 && g_realPresent1)
		{
			DWORD protection = 0;
			if (VirtualProtect(&g_vtable1[22], sizeof(void *), PAGE_READWRITE, &protection))
			{
				g_vtable1[22] = reinterpret_cast<void *>(g_realPresent1);
				VirtualProtect(&g_vtable1[22], sizeof(void *), protection, &protection);
			}
		}

		logFormat("MCOnly: hook removed after %llu drawn frames", (unsigned long long)g_drawn);
		g_installed = false;
	}
}

namespace present
{
	bool try_register(void *module)
	{
		if (g_installed)
		{
			return true;
		}

		if (g_broken)
		{
			return false;
		}

		if (!installHook())
		{
			return false;
		}

		logLine("MCOnly: presenter armed; GTA's world is drawn and then replaced by Minecraft's");
		return true;
	}

	void unregister(void *module)
	{
		g_unloading = true;
		removeHook();
		g_lastMapTry = 0;
	}

	void set_active(bool active)
	{
		g_active = active;
	}

	void set_host_planes(float near_clip, float far_clip)
	{
		// No depth compositing: nothing to linearise.
	}

	void set_host_pose(float yaw, float pitch, float roll, float fov, double x, double y, double z)
	{
		// No re-projection: Minecraft's frame is shown exactly as it rendered it.
	}

	void set_pose_lag(int frames)
	{
	}

	void set_camera_locked(bool locked)
	{
	}

	void set_look(float light_match, float depth_bias, float slope_bias)
	{
		// Minecraft's own lighting, not GTA's.
	}

	void set_screen_fx(float shake_x, float shake_y, float shake_roll, float portal_warp)
	{
	}

	void backbuffer_size(int &width, int &height)
	{
		width = g_backW;
		height = g_backH;
	}
}
